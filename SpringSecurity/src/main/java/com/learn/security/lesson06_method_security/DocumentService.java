package com.learn.security.lesson06_method_security;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * LESSON 6 — the service the annotations live on.
 *
 * Note there is NOT ONE security if-statement in the method bodies.
 * The rules sit ON the methods, declarative, next to the logic they
 * protect — reviewable in the same diff, impossible to "forget to call".
 */
@Service
@Profile("lesson06")
public class DocumentService {

    /** Fake table: id -> (owner, content). */
    private final Map<Long, Document> documents = Map.of(
            1L, new Document(1L, "alice", "alice's tax return"),
            2L, new Document(2L, "bob", "bob's secret plan")
    );

    public record Document(Long id, String owner, String content) {}

    /**
     * PARAMETER-based rule. `#owner` = the argument. Callers may list
     * only their own documents — unless they hold ADMIN.
     */
    @PreAuthorize("#owner == authentication.name or hasRole('ADMIN')")
    public List<Document> findByOwner(String owner) {
        return documents.values().stream()
                .filter(d -> d.owner().equals(owner))
                .toList();
    }

    /**
     * RETURN-VALUE-based rule. We cannot know the owner until the row is
     * loaded, so the check runs AFTER the method, on `returnObject`.
     * Fails -> the value is discarded and the caller gets 403.
     */
    @PostAuthorize("returnObject.owner == authentication.name or hasRole('ADMIN')")
    public Document findById(Long id) {
        Document doc = documents.get(id);
        if (doc == null) throw new NoSuchElementException("no document " + id);
        return doc;
    }

    /** Simple role gate — same as a URL rule, but travels with the method. */
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        // pretend delete; the point is whether you can CALL this at all
    }

    /*
     * TRAP (do not copy): this method calls findById THROUGH `this`,
     * bypassing the proxy — the @PostAuthorize on findById DOES NOT RUN.
     *
     *   public Document sneakyRead(Long id) {
     *       return this.findById(id);   // no security check happens!
     *   }
     *
     * Self-invocation is the #1 method-security bug in production code.
     * Fix: put the annotated method in another bean, or check earlier.
     */
}
