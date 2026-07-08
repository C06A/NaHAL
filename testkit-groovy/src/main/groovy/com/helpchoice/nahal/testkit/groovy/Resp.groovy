package com.helpchoice.nahal.testkit.groovy

import com.helpchoice.nahal.testkit.Response

/**
 * Groovy facade over the Kotlin {@link Response}. Delegates the accessors (code, headers, cookies,
 * asText, asBytes, asFile, isSuccess) and overrides {@link #asHal} to return a navigable {@link Hal}
 * so a test can keep following links: {@code res.GET('orders').asHal()['items'].sku}.
 */
class Resp {

    @Delegate
    final Response response

    Resp(Response response) {
        this.response = response
    }

    Hal asHal() { new Hal(response.asHal()) }
}
