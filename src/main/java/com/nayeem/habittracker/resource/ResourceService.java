package com.nayeem.habittracker.resource;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.common.pagination.PageRequests;
import com.nayeem.habittracker.common.response.PageResponse;
import com.nayeem.habittracker.file.FileDownload;
import com.nayeem.habittracker.file.FileService;
import com.nayeem.habittracker.goal.Goal;
import com.nayeem.habittracker.goal.GoalService;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Notes, links and files of the signed-in user. Every method takes the acting user's id (from the security
 * context, never the request) and only ever sees that user's resources — anyone else's is 404.
 * Note text, addresses and file names are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    static final int MAX_BODY = 20_000;

    /** Pinned first, then newest. */
    private static final Sort ORDER = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"));

    private final ResourceRepository resourceRepository;
    private final UserService userService;
    private final GoalService goalService;
    private final FileService fileService;

    @Transactional
    public ResourceResponse create(Long userId, ResourceRequest request) {
        Resource resource = new Resource();
        resource.setUser(userService.getById(userId));
        apply(userId, resource, request);
        resource = resourceRepository.save(resource);
        log.info("Resource {} ({}) created by user {}", resource.getId(), resource.getType(), userId);
        return ResourceResponse.from(resource);
    }

    /**
     * Saves an uploaded file as a {@code FILE} resource. One transaction: if the resource can't be
     * saved, the stored bytes are removed again ({@link FileService#store}).
     */
    @Transactional
    public ResourceResponse createFile(Long userId, ResourceFileRequest request, MultipartFile upload) {
        Resource resource = new Resource();
        resource.setUser(userService.getById(userId));
        resource.setType(ResourceType.FILE);
        resource.setTitle(request.getTitle().trim());
        resource.setBody(blankToNull(request.getBody()));
        // checked before the upload is stored, so a refused request writes no bytes
        resource.setGoal(ownedGoal(userId, request.getGoalId()));
        resource.setPinned(Boolean.TRUE.equals(request.getPinned()));
        resource.setFile(fileService.store(userId, upload));
        resource = resourceRepository.save(resource);
        log.info("Resource {} (FILE) created by user {}", resource.getId(), userId);
        return ResourceResponse.from(resource);
    }

    @Transactional(readOnly = true)
    public ResourceResponse get(Long userId, Long resourceId) {
        return ResourceResponse.from(find(userId, resourceId));
    }

    /** The user's resources, pinned first; each filter is optional. {@code q}: title contains, any case. */
    @Transactional(readOnly = true)
    public PageResponse<ResourceResponse> list(Long userId, Long goalId, ResourceType type, String q,
                                               int page, int size) {
        List<Specification<Resource>> filters = new ArrayList<>();
        filters.add((root, query, cb) -> cb.equal(root.get("user").get("id"), userId));
        if (goalId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("goal").get("id"), goalId));
        }
        if (type != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            filters.add((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\'));
        }
        var clamped = PageRequests.unsorted(page, size);
        var pageable = PageRequest.of(clamped.getPageNumber(), clamped.getPageSize(), ORDER);
        return PageResponse.from(resourceRepository.findAll(Specification.allOf(filters), pageable),
                ResourceResponse::from);
    }

    /** One goal's resources, pinned first; 404 when the goal isn't the user's. */
    @Transactional(readOnly = true)
    public PageResponse<ResourceResponse> listForGoal(Long userId, Long goalId, int page, int size) {
        goalService.getOwnedGoal(userId, goalId);
        return list(userId, goalId, null, null, page, size);
    }

    /** Full replace: every field of the request, as on create. */
    @Transactional
    public ResourceResponse update(Long userId, Long resourceId, ResourceRequest request) {
        Resource resource = find(userId, resourceId);
        apply(userId, resource, request);
        return ResourceResponse.from(resourceRepository.saveAndFlush(resource));
    }

    @Transactional
    public ResourceResponse setPinned(Long userId, Long resourceId, boolean pinned) {
        Resource resource = find(userId, resourceId);
        if (resource.isPinned() != pinned) {
            resource.setPinned(pinned);
            resource = resourceRepository.saveAndFlush(resource);
        }
        return ResourceResponse.from(resource);
    }

    /** A FILE resource's bytes, for download; 404 when it isn't the user's or has no file. */
    @Transactional(readOnly = true)
    public FileDownload download(Long userId, Long resourceId) {
        Resource resource = find(userId, resourceId);
        if (resource.getFile() == null) {
            throw new ApplicationException(ErrorCode.FILE_NOT_FOUND);
        }
        return fileService.open(resource.getFile());
    }

    /** A FILE resource takes its file with it; the bytes go once the delete has committed. */
    @Transactional
    public void delete(Long userId, Long resourceId) {
        Resource resource = find(userId, resourceId);
        resourceRepository.delete(resource);
        if (resource.getFile() != null) {
            fileService.delete(resource.getFile());
        }
        log.info("Resource {} deleted by user {}", resourceId, userId);
    }

    private Resource find(Long userId, Long resourceId) {
        return resourceRepository.findByIdAndUserId(resourceId, userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** Create and full replace. A file is never added or swapped here — only its title, body, goal, pin. */
    private void apply(Long userId, Resource resource, ResourceRequest request) {
        String body = blankToNull(request.getBody());
        String url = blankToNull(request.getUrl());
        boolean isFile = resource.getType() == ResourceType.FILE;
        if (request.getType() == ResourceType.FILE && !isFile) {
            throw invalid("Upload files with POST /api/resources/files");
        }
        if (isFile && request.getType() != ResourceType.FILE) {
            throw invalid("A file resource stays a FILE; save a new resource instead");
        }
        switch (request.getType()) {
            case NOTE -> {
                if (body == null) {
                    throw invalid("A note needs a body");
                }
                if (url != null) {
                    throw invalid("A note has no url; make it a LINK");
                }
            }
            case LINK -> {
                if (url == null) {
                    throw invalid("A link needs a url");
                }
                requireHttpUrl(url);
            }
            case FILE -> {
                if (url != null) {
                    throw invalid("A file has no url");
                }
            }
        }
        resource.setType(request.getType());
        resource.setTitle(request.getTitle().trim());
        resource.setBody(body);
        resource.setUrl(url);
        resource.setGoal(ownedGoal(userId, request.getGoalId()));
        resource.setPinned(Boolean.TRUE.equals(request.getPinned()));
    }

    private Goal ownedGoal(Long userId, Long goalId) {
        return goalId == null ? null : goalService.getOwnedGoal(userId, goalId);
    }

    /** Only absolute http(s) addresses with a host: no {@code javascript:}, {@code file:} or {@code data:}. */
    static void requireHttpUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!httpScheme || uri.getHost() == null || uri.getUserInfo() != null) {
                throw invalid("The url must be an http or https address");
            }
        } catch (URISyntaxException e) {
            throw invalid("The url is not a valid address");
        }
    }

    private static ApplicationException invalid(String message) {
        return new ApplicationException(ErrorCode.RESOURCE_INVALID, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
