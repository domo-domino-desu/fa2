package me.domino.fa2.desktop.e2e

/** One result per primary navigation, plus one complete flow per reusable detail-page family. */
internal fun onlineScenarios(ui: TaoUiDriver): LinkedHashMap<String, suspend () -> Unit> =
    linkedMapOf<String, suspend () -> Unit>(
            "nav-feed" to
                {
                  ui.click("nav-feed")
                  ui.get("submission-waterfall-card")
                  ui.scroll()
                  ui.scroll(down = false)
                  ui.pagination(jump = false)
                  listRoundtrip(ui)
                },
            "nav-browse" to
                {
                  ui.click("nav-browse")
                  ui.get("submission-waterfall-card")
                  ui.pagination()
                  listRoundtrip(ui)
                },
            "nav-search" to
                {
                  search(ui)
                  ui.pagination()
                  listRoundtrip(ui)
                },
            "nav-more" to
                {
                  // Populate histories with real UI visits so this scenario also runs alone on a
                  // fresh profile.
                  ui.click("nav-browse")
                  openSubmission(ui)
                  ui.get("detail-pager")
                  ui.back()
                  search(ui)
                  ui.click("nav-more")
                  ui.click("more-history")
                  openSubmission(ui)
                  ui.get("detail-pager")
                  ui.back()
                  ui.back()
                  ui.click("more-search-history")
                  ui.click("search-history-item")
                  ui.get("submission-waterfall-card")
                  ui.back()
                  ui.back()
                  ui.click("more-settings")
                  ui.get("settings-screen")
                  ui.back()
                  ui.click("more-about")
                  ui.get("about-screen")
                  ui.back()
                },
            "artist-page" to
                {
                  discoverUserSection(ui, "gallery") {
                    ui.pagination()
                    listRoundtrip(ui)
                  }
                  discoverUserSection(ui, "favorites") {
                    ui.pagination(jump = false)
                    listRoundtrip(ui)
                  }
                  discoverUserSection(ui, "journals") {
                    ui.click("journal-card")
                    ui.get("journal-pager")
                    ui.pagerRoundtrip("journal-pager")
                    ui.back()
                  }
                  discoverUserSection(ui, "followers") {
                    ui.click("user-card")
                    ui.get("user-pager")
                    ui.pagerRoundtrip("user-pager")
                    ui.back()
                    ui.back()
                  }
                },
            "post-page" to
                {
                  ui.click("nav-browse")
                  val anchor = ui.scroll()
                  openSubmission(ui)
                  ui.get("detail-pager")
                  ui.pagerRoundtrip()
                  ui.click("image-open")
                  ui.zoom()
                  val page = ui.state("detail-pager").substringBefore(';')
                  ui.reveal("detail-author", "detail-scroll")
                  ui.click("detail-author")
                  ui.get("user-gallery")
                  ui.back()
                  ui.until("post-position-restored") {
                    ui.state("detail-pager").substringBefore(';') == page
                  }
                  ui.back()
                  ui.until("post-return-list-restored") { ui.state("browse-list") == anchor }
                },
        )
        .apply {
          if (System.getProperty("e2e.case") == "tao-adapter-smoke")
              put("tao-adapter-smoke") {
                ui.click("nav-browse")
                ui.get("submission-waterfall-card")
                ui.pagination()
              }
        }

private suspend fun search(ui: TaoUiDriver) {
  ui.click("nav-search")
  ui.click("search-open")
  ui.type("search-input", System.getProperty("e2e.query", "wolf"))
  ui.click("search-submit")
  ui.get("submission-waterfall-card")
}

private suspend fun listRoundtrip(ui: TaoUiDriver) {
  val anchor = ui.scroll()
  openSubmission(ui)
  ui.get("detail-pager")
  ui.back()
  ui.until("list-viewport-restored") { ui.state("browse-list") == anchor }
}

/** Select suitable live content through the UI; missing live data never counts as a pass. */
private suspend fun discoverUserSection(
    ui: TaoUiDriver,
    section: String,
    verify: suspend () -> Unit,
) {
  ui.root()
  ui.click("nav-browse")
  repeat(12) {
    openSubmission(ui)
    ui.reveal("detail-author", "detail-scroll")
    ui.click("detail-author")
    ui.get("user-gallery")
    if (section == "followers" && ui.find("browse-list") != null) ui.scrollToStart()
    if (!ui.probe { ui.find("user-$section") != null }) {
      ui.root()
      ui.click("nav-browse")
      ui.scroll()
      return@repeat
    }
    ui.click("user-$section")
    val suitable =
        when (section) {
          "journals" -> ui.probe { ui.count("journal-card") >= 2 }
          "followers" -> ui.probe { ui.count("user-card") >= 2 }
          else -> {
            val menuReady = ui.probe { ui.find("page-toggle") != null }
            if (!menuReady) false
            else {
              if (ui.find("page-next") == null) ui.click("page-toggle")
              ui.get("page-next")
              ui.until("artist-pagination-ready") {
                ui.state("page-menu").contains("loading=false")
              }
              // A one-page gallery has no next page. Its last action may simply retain
              // the cached page without resolving a numeric total; select another artist.
              if (section == "gallery" && !ui.enabled("page-next")) false
              else {
                if (section == "gallery" && ui.enabled("page-last")) {
                  ui.click("page-last")
                  ui.until("gallery-last-page-resolved") {
                    !ui.state("page-menu").contains("loading=true") &&
                        Regex("last=(\\d+)").containsMatchIn(ui.state("page-menu"))
                  }
                }
                if (section == "favorites") ui.enabled("page-next")
                else
                    Regex("last=(\\d+)")
                        .find(ui.state("page-menu"))
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                        ?.let { it >= 3 } == true
              }
            }
          }
        }
    if (suitable) {
      verify()
      ui.root()
      return
    }
    ui.root()
    ui.click("nav-browse")
    ui.scroll()
  }
  error("No suitable live content for required user section: $section")
}

/** Follow the real two-step reveal/open interaction for blurred blocked submissions. */
private suspend fun openSubmission(ui: TaoUiDriver) {
  ui.get("submission-waterfall-card")
  if (ui.state("submission-waterfall-card").contains("blocked=true")) {
    ui.click("submission-waterfall-card")
    ui.until("blocked-preview-revealed") {
      ui.state("submission-waterfall-card").contains("blocked=false")
    }
  }
  ui.click("submission-waterfall-card")
}
