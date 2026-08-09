(function () {
    if (window.hideFeedNoiseInjected) return;
    window.hideFeedNoiseInjected = true;

    const HIDDEN_ATTR = "data-lite-hidden-feed";

    // Feed-like pages only (the home feed and its /feed variant). Community posts and the
    // shorts shelf are removed there; posts stay visible on watch/channel pages.
    function isFeedPage() {
        const path = window.location.pathname.replace(/\/+$/, "");
        return path === "" || path === "/feed";
    }

    function hideEl(element) {
        if (!(element instanceof HTMLElement) || element.getAttribute(HIDDEN_ATTR) === "1") return;
        element.setAttribute(HIDDEN_ATTR, "1");
        element.style.display = "none";
    }

    // Climb from a post/shorts element to the nearest feed item or shelf container and hide
    // that container (prefer the item, fall back to the enclosing shelf/section).
    function hideItem(element) {
        if (!(element instanceof HTMLElement)) return;
        const container = element.closest(
            "ytm-post-renderer, ytm-post-multi-image-renderer, ytd-post-renderer, " +
            "ytd-backstage-post-renderer, ytm-rich-item-renderer, ytd-rich-item-renderer"
        ) || element.closest(
            "ytm-reel-shelf-renderer, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer, " +
            "ytm-rich-section-renderer, ytd-rich-section-renderer"
        ) || element;
        hideEl(container);
    }

    // Every community post links to /post/<id> — the only stable signature across the
    // mobile and desktop layouts (element names keep changing).
    function hideCommunityPosts() {
        if (!isFeedPage()) return;
        document.querySelectorAll(
            "ytm-post-renderer, ytm-post-multi-image-renderer, ytd-post-renderer, " +
            "ytd-backstage-post-renderer, a[href*=\"/post/\"]"
        ).forEach(hideItem);
    }

    // Shorts shelf on the home feed: mobile (ytm-reel-shelf-renderer) and desktop
    // (ytd-rich-shelf-renderer / ytd-reel-shelf-renderer with a "Shorts" header or
    // /shorts links), plus shorts items embedded directly in the rich grid.
    function hideShortsShelf() {
        if (!isFeedPage()) return;
        document.querySelectorAll(
            "ytm-reel-shelf-renderer, ytd-reel-shelf-renderer, ytd-rich-shelf-renderer"
        ).forEach((shelf) => {
            const header = (shelf.querySelector('[id*="title"], h2, h3')?.innerText || "").trim();
            const isShorts = /^shorts/i.test(header)
                || shelf.querySelector('a[href*="/shorts"]')
                || shelf.querySelector("ytm-shorts-lockup-view-model");
            if (isShorts) hideEl(shelf);
        });
        document.querySelectorAll('a[href*="/shorts/"]').forEach((link) => {
            const item = link.closest("ytd-rich-item-renderer, ytm-rich-item-renderer");
            if (item) hideEl(item);
        });
    }

    function scan() {
        hideCommunityPosts();
        hideShortsShelf();
    }

    // The feed renders content lazily on scroll, so watch for new nodes and re-scan,
    // throttled to once per animation frame.
    let scheduled = false;
    function scheduleScan() {
        if (scheduled) return;
        scheduled = true;
        requestAnimationFrame(() => {
            scheduled = false;
            scan();
        });
    }

    const observer = new MutationObserver(scheduleScan);
    observer.observe(document.documentElement, { childList: true, subtree: true });

    // YouTube is an SPA: also re-scan after in-app navigation back to the home feed.
    window.addEventListener("yt-navigate-finish", scan, true);

    scan();
})();
