package com.helpchoice.nahal.core

import com.helpchoice.nahal.haldish.http.HalHttpResponse
import com.helpchoice.nahal.haldish.model.HalDocument
import com.helpchoice.nahal.haldish.model.HalLink

interface NavigationPlugin {

    fun initialize(config: NavigatorConfig)

    fun preRequest(link: HalLink, resource: HalDocument): HalLink

    fun postResponse(response: HalHttpResponse): HalHttpResponse
}
