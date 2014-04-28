package com.rackspace.papi.components.cnorm

import com.rackspace.papi.components.cnorm.normalizer.HeaderNormalizer
import com.rackspace.papi.components.normalization.config.HeaderFilterList
import com.rackspace.papi.components.normalization.config.HttpHeader
import com.rackspace.papi.components.normalization.config.HttpHeaderList
import com.rackspace.papi.filter.logic.FilterDirector
import com.rackspace.papi.filter.logic.impl.FilterDirectorImpl
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when


class HeaderNormalizerTest extends Specification {

    HttpServletRequest request
    FilterDirector director
    HttpHeader authHeader, userHeader, groupHeader, contentType
    HeaderNormalizer headerNormalizer

    HttpHeaderList blacklist = new HttpHeaderList()
    HttpHeaderList whitelist = new HttpHeaderList()
    HeaderFilterList headerFilterList = new HeaderFilterList()

    def setup() {
        request = mock(HttpServletRequest.class)

        director = new FilterDirectorImpl()

        authHeader = new HttpHeader()
        userHeader = new HttpHeader()
        groupHeader = new HttpHeader()
        contentType = new HttpHeader()

        authHeader.setId("X-Auth-Header")
        userHeader.setId("X-User-Header")

        blacklist.getHeader().add(authHeader)
        blacklist.getHeader().add(userHeader)

        groupHeader.setId("X-Group-Header")
        contentType.setId("Content-Type")

        whitelist.getHeader().add(groupHeader)
        whitelist.getHeader().add(contentType)

        def requestHeaders = ["X-Auth-Header", "Content-Type", "X-User-Header", "Accept", "X-Group-Header", "X-Auth-Token"]
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(requestHeaders))

        headerFilterList.setBlacklist(blacklist)
        headerFilterList.setWhitelist(whitelist)
        headerNormalizer = new HeaderNormalizer(headerFilterList, true)
    }

    def "properly handles whitelist and blacklist"() {
        when:
        headerNormalizer.normalizeHeaders(request,director)

        def headersToRemove = director.requestHeaderManager().headersToRemove()

        then:
        headersToRemove.contains(authHeader.getId())
        headersToRemove.contains(userHeader.getId())
        !headersToRemove.contains("accept")
        !headersToRemove.contains(groupHeader.getId())
        !headersToRemove.contains(contentType.getId())
    }

}
