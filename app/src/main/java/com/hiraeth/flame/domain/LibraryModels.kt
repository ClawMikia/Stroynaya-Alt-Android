package com.hiraeth.flame.domain

/** Filter chips for the library grid/list. */
enum class MediaTypeFilter {
    All,
    ImagesOnly,
}

/** Sort options mapped to UI and comparator logic in [com.hiraeth.flame.ui.library.LibraryViewModel]. */
enum class LibrarySort {
    DateNewest,
    DateOldest,
    NameAZ,
    NameZA,
    SizeLargest,
    SizeSmallest,
}

enum class LibraryViewMode {
    Grid,
    List,
}
