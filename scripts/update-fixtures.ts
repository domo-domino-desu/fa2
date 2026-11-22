import { run } from "./fixture-tools";

const targets = [
  { file: "home-guest.html", path: "/", anonymous: true },
  { file: "watchers.html", path: "/watchlist/to/fender/" },
  { file: "watching.html", path: "/watchlist/by/fender/" },
  { file: "favorites.html", path: "/favorites/fender/" },
  { file: "submission.html", path: "/view/67119566/" },
  {
    file: "home.html",
    path: "/",
  },
  {
    file: "browse.html",
    path: "/browse/",
  },
  {
    file: "search.html",
    path: "/search/?q=wolf",
  },
  {
    file: "announcements.html",
    path: "/announcements",
  },
  {
    file: "changelog.html",
    path: "/changelog",
  },
  {
    file: "user.html",
    path: "/user/fender/",
  },
  {
    file: "gallery.html",
    path: "/gallery/fender/",
  },
  {
    file: "journals.html",
    path: "/journals/fender/",
  },
  {
    file: "journal.html",
    path: "/journal/11377368/",
  },
];

await run("https://www.furaffinity.net", targets);
