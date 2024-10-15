package com.example.thestudentapp.network

import com.example.thestudentapp.models.ContentModel

/// This [NetworkMessageInterface] acts as an interface.
interface NetworkMessageInterface {
    fun onContent(content: ContentModel)
}