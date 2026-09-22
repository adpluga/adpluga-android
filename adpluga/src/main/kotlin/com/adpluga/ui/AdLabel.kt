package com.adpluga.ui

import com.adpluga.model.Ad
import com.adpluga.model.Slide

/**
 * Content description announced in place of the creative. An ad is never
 * decorative — it carries meaning and opens a destination — so an unlabelled
 * image leaves the tap target with no name at all (WCAG 2.2 SC 1.1.1 and
 * SC 2.4.4, both level A). Falls back to the title, then to a neutral word,
 * which is still a name.
 */
internal fun adLabel(ad: Ad): String =
    ad.altText?.takeIf { it.isNotBlank() }
        ?: ad.nativeAssets?.get("title")?.takeIf { it.isNotBlank() }
        ?: "Anuncio"

/**
 * Slides share the ad's destination, so a card with no copy of its own
 * inherits the ad's label rather than going unnamed.
 */
internal fun slideLabel(ad: Ad, slide: Slide): String =
    slide.title?.takeIf { it.isNotBlank() } ?: adLabel(ad)
