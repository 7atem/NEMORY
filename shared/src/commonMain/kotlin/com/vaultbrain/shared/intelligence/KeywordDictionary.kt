package com.vaultbrain.shared.intelligence

import com.vaultbrain.shared.domain.LensId

object KeywordDictionary {
    
    data class SubModule(val name: String, val keywords: Set<String>)
    val HIERARCHY = mapOf(
        LensId.MONEY to listOf(
            SubModule("grocery", setOf(
                "eggs", "milk", "bread", "cheese", "butter", "chicken", "beef", "pork",
                "fish", "rice", "pasta", "cereal", "yogurt", "apples", "bananas", "oranges",
                "tomatoes", "potatoes", "onions", "garlic", "coffee", "tea", "juice", "soda",
                "beer", "wine", "chips", "cookies", "candy", "chocolate", "ice cream",
                "supermarket", "grocery", "groceries",
                "walmart", "target", "costco", "kroger", "safeway", "publix", "aldi", "trader joe's",
                "whole foods", "carrefour", "tesco", "sainsbury", "asda", "morrisons", "waitrose"
            )),
            SubModule("household", setOf(
                "soap", "shampoo", "toothpaste", "toilet paper", "paper towels", "laundry detergent",
                "dish soap", "trash bags", "diapers", "wipes", "dog food", "cat food"
            )),
            SubModule("electronics", setOf(
                "laptop", "phone", "smartphone", "tablet", "headphones", "charger", "cable",
                "camera", "speaker", "monitor", "keyboard", "mouse", "apple store", "best buy"
            )),
            SubModule("clothing", setOf(
                "shirt", "pants", "shoes", "dress", "jacket", "coat", "socks", "underwear",
                "apparel", "boutique", "zara", "h&m", "uniqlo", "nike", "adidas"
            )),
            SubModule("cleaning", setOf(
                "dry clean", "laundry", "cleaning", "maid"
            )),
            SubModule("shipping", setOf(
                "delivery", "shipping", "fedex", "ups", "usps", "dhl", "post office", "tracking number"
            )),
            SubModule("personal_care", setOf(
                "salon", "haircut", "spa", "massage", "gym", "fitness", "yoga", "barber"
            )),
            SubModule("home_service", setOf(
                "plumber", "electrician", "contractor", "landscaping", "pest control", "moving", "storage"
            ))
        ),
        LensId.TRAVEL to listOf(
            SubModule("fuel", setOf(
                "fuel", "gas", "petrol", "diesel", "gas station", "shell", "chevron", "exxon"
            )),
            SubModule("maintenance", setOf(
                "oil", "tire", "brake", "engine", "battery", "transmission", "suspension",
                "exhaust", "alignment", "mechanic", "garage", "auto parts", "car wash", "dealership"
            )),
            SubModule("admin", setOf(
                "registration", "license plate", "dmv", "bmv", "mva", "rmv", "dot", "mot",
                "insurance policy", "vehicle registration"
            ))
        ),
        LensId.HEALTH to listOf(
            SubModule("medical", setOf(
                "doctor", "dentist", "hospital", "clinic", "x-ray", "mri", "blood test", "lab result",
                "copay", "deductible", "optometrist", "physician", "pediatrician"
            )),
            SubModule("pharmacy", setOf(
                "pharmacy", "prescription", "medication", "pill", "tablet", "capsule", "syringe",
                "vaccine", "cvs", "walgreens", "rite aid", "boots", "apothecary"
            ))
        ),
        LensId.BUREAUCRACY to listOf(
            SubModule("furniture", setOf(
                "sofa", "bed", "chair", "table", "desk", "wardrobe", "cabinet", "ikea", "wayfair"
            )),
            SubModule("appliance", setOf(
                "appliance", "tv", "refrigerator", "oven", "microwave", "dishwasher", "washer", "dryer", "vacuum"
            )),
            SubModule("hardware", setOf(
                "tool", "hardware", "lumber", "paint", "plumbing", "electrical", "garden", "lawn",
                "patio", "home depot", "lowes", "menards", "ace hardware"
            ))
        ),
        LensId.MEDIA to listOf(
            SubModule("movie", setOf(
                "movie", "film", "cinema", "showtimes", "dvd", "blu-ray"
            )),
            SubModule("book", setOf(
                "book", "novel", "paperback", "hardcover", "kindle", "audible", "bookstore"
            )),
            SubModule("streaming", setOf(
                "netflix", "hulu", "amazon prime", "disney+", "hbo", "apple tv"
            )),
            SubModule("music", setOf(
                "music", "album", "concert", "spotify", "apple music", "youtube", "vinyl"
            )),
            SubModule("gaming", setOf(
                "video game", "playstation", "xbox", "nintendo", "steam", "epic games"
            ))
        )
    )

    private val COMPILED_HIERARCHY: Map<String, List<Pair<String, Regex>>> by lazy {
        HIERARCHY.mapValues { (_, subModules) ->
            subModules.map { sub ->
                val sorted = sub.keywords.sortedByDescending { it.length }
                val pattern = sorted.joinToString("|") { Regex.escape(it.lowercase()) }
                sub.name to Regex("(?i)(?<=^|[^\\p{L}\\p{N}])(?:$pattern)(?=[^\\p{L}\\p{N}]|$)")
            }
        }
    }

    /**
     * Returns a map of detected Main Module (LensId) to its matched Sub Module name.
     */
    fun detectHierarchy(text: String): Map<String, String> {
        val detected = mutableMapOf<String, String>()
        
        for ((mainModule, compiledSubModules) in COMPILED_HIERARCHY) {
            for ((subModuleName, regex) in compiledSubModules) {
                if (regex.containsMatchIn(text)) {
                    detected[mainModule] = subModuleName
                    break // Stop at the first matched sub-module for this main module
                }
            }
        }
        
        return detected
    }
}
