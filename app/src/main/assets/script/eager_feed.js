(function () {
    if (window.eagerFeedInjected) return;
    window.eagerFeedInjected = true;

    // DeX Mode only. In DeX the WebView uses the desktop User-Agent (see
    // YoutubeWebview.setDeXDesktopMode), so YouTube serves the desktop layout. The mobile
    // layout keeps its stock pagination behavior and is left completely untouched.
    if (/Mobile/i.test(navigator.userAgent)) return;

    // Keep this many ROWS of grid items (5 columns each on the desktop layout) buffered
    // below the viewport, so the user never waits for the next batch at the bottom.
    const MIN_ROWS_AHEAD = 10;
    // Aggressive polling: as soon as the previous batch's continuation sentinel appears,
    // the next batch is triggered (back-to-back fetching until the target is reached).
    const CHECK_INTERVAL_MS = 250;
    // Only yield to the user's own scrolling for this long before resuming prefetching.
    const SCROLL_FIGHT_MS = 150;

    function isFeedPage() {
        const path = window.location.pathname.replace(/\/+$/, "");
        return path === "" || path === "/feed";
    }

    function grid() {
        return document.querySelector("ytd-rich-grid-renderer, ytm-rich-grid-renderer");
    }

    function feedItems() {
        return document.querySelectorAll("ytd-rich-item-renderer, ytm-rich-item-renderer");
    }

    // YouTube's "load more" sentinel at the bottom of the grid; it is replaced as batches
    // load and disappears once the feed is fully loaded.
    function sentinel() {
        return document.querySelector(
            "ytd-continuation-item-renderer, ytm-continuation-item-renderer, " +
            "ytd-rich-grid-renderer #continuation, ytm-rich-grid-renderer #continuation"
        );
    }

    // Number of columns in the current grid (5 on the desktop home feed).
    function columns() {
        const items = feedItems();
        if (items.length < 2) return 5;
        const firstRowTop = items[0].offsetTop;
        let count = 1;
        while (count < items.length && items[count].offsetTop === firstRowTop) count++;
        return Math.max(2, count);
    }

    // Enough items to fill the viewport plus the requested number of rows ahead.
    function targetItemCount() {
        const items = feedItems();
        if (items.length === 0) return MIN_ROWS_AHEAD * 5;
        const rowHeight = items[0].offsetHeight || 280;
        const visibleRows = Math.max(1, Math.ceil(window.innerHeight / rowHeight));
        return (visibleRows + MIN_ROWS_AHEAD) * columns();
    }

    let lastScrollTime = 0;
    window.addEventListener("scroll", () => {
        lastScrollTime = Date.now();
    }, { passive: true });

    function maybeLoadMore() {
        if (document.hidden) return;          // don't prefetch while the tab is hidden
        if (!isFeedPage()) return;
        if (!grid()) return;
        const items = feedItems();
        if (items.length >= targetItemCount()) return; // enough content buffered already
        if (Date.now() - lastScrollTime < SCROLL_FIGHT_MS) return; // user is scrolling; don't fight them
        const cont = sentinel();
        if (!cont || cont.offsetParent === null) return; // not mounted / feed fully loaded

        // Silently nudge the sentinel into the viewport long enough for YouTube's
        // IntersectionObserver to fire (it decides to load the next batch), then restore
        // the scroll position two frames later — the user never sees the jump.
        const scrollY = window.scrollY;
        cont.scrollIntoView({ block: "center", behavior: "auto" });
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                window.scrollTo(0, scrollY);
            });
        });
    }

    setInterval(maybeLoadMore, CHECK_INTERVAL_MS);
})();
