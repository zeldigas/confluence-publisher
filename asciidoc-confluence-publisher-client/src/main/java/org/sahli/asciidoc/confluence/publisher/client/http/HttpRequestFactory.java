/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sahli.asciidoc.confluence.publisher.client.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.message.BasicHeader;
import org.sahli.asciidoc.confluence.publisher.client.http.payloads.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.http.entity.ContentType.APPLICATION_OCTET_STREAM;
import static org.sahli.asciidoc.confluence.publisher.client.http.HttpRequestFactory.PagePayloadBuilder.pagePayloadBuilder;
import static org.sahli.asciidoc.confluence.publisher.client.utils.AssertUtils.assertMandatoryParameter;

/**
 * @author Alain Sahli
 */
class HttpRequestFactory {

    private final static Header APPLICATION_JSON_UTF8_HEADER = new BasicHeader("Content-Type", "application/json;charset=utf-8");
    private static final String REST_API_CONTEXT = "/rest/api";
    private static final int INITAL_VERSION = 1;
    private final String rootConfluenceUrl;
    private final String confluenceRestApiEndpoint;

    HttpRequestFactory(String rootConfluenceUrl) {
        assertMandatoryParameter(isNotBlank(rootConfluenceUrl), "rootConfluenceUrl");

        this.rootConfluenceUrl = rootConfluenceUrl;
        this.confluenceRestApiEndpoint = rootConfluenceUrl + REST_API_CONTEXT;
    }

    HttpPost addBlogPostRequest(String spaceKey, String title, String content, LocalDate publishDate, String versionMessage) {
        assertMandatoryParameter(isNotBlank(spaceKey), "spaceKey");
        assertMandatoryParameter(isNotBlank(title), "title");

        BlogPostPayload pagePayload = pagePayloadBuilder()
                .spaceKey(spaceKey)
                .title(title)
                .content(content)
                .publishDate(publishDate)
                .version(INITAL_VERSION)
                .versionMessage(versionMessage)
                .buildPost();

        return addPageHttpPost(this.confluenceRestApiEndpoint, pagePayload);
    }

    HttpPut updateBlogPostRequest(String contentId, String title, String content, LocalDate publishDate, int newVersion, String versionMessage) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(title), "title");

        BlogPostPayload pagePayload = pagePayloadBuilder()
                .title(title)
                .content(content)
                .publishDate(publishDate)
                .version(newVersion)
                .versionMessage(versionMessage)
                .buildPost();

        HttpPut updatePageRequest = new HttpPut(this.confluenceRestApiEndpoint + "/content/" + contentId);
        updatePageRequest.setEntity(httpEntityWithJsonPayload(pagePayload));
        updatePageRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        return updatePageRequest;
    }

    HttpPost addPageUnderAncestorRequest(String spaceKey, String ancestorId, String title, String content, String versionMessage) {
        assertMandatoryParameter(isNotBlank(spaceKey), "spaceKey");
        assertMandatoryParameter(isNotBlank(ancestorId), "ancestorId");
        assertMandatoryParameter(isNotBlank(title), "title");

        PagePayload pagePayload = pagePayloadBuilder()
                .spaceKey(spaceKey)
                .ancestorId(ancestorId)
                .title(title)
                .content(content)
                .version(INITAL_VERSION)
                .versionMessage(versionMessage)
                .build();

        return addPageHttpPost(this.confluenceRestApiEndpoint, pagePayload);
    }

    HttpPut updatePageRequest(String contentId, String ancestorId, String title, String content, int newVersion, String versionMessage) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(title), "title");

        PagePayload pagePayload = pagePayloadBuilder()
                .ancestorId(ancestorId)
                .title(title)
                .content(content)
                .version(newVersion)
                .versionMessage(versionMessage)
                .build();

        HttpPut updatePageRequest = new HttpPut(this.confluenceRestApiEndpoint + "/content/" + contentId);
        updatePageRequest.setEntity(httpEntityWithJsonPayload(pagePayload));
        updatePageRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        return updatePageRequest;
    }

    HttpDelete deletePageRequest(String contentId) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");

        return new HttpDelete(this.confluenceRestApiEndpoint + "/content/" + contentId);
    }

    HttpPost addAttachmentRequest(String contentId, String attachmentFileName, InputStream attachmentContent) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(attachmentFileName), "attachmentFileName");
        assertMandatoryParameter(attachmentContent != null, "attachmentContent");

        HttpPost attachmentPostRequest = new HttpPost(this.confluenceRestApiEndpoint + "/content/" + contentId + "/child/attachment");
        attachmentPostRequest.addHeader(new BasicHeader("X-Atlassian-Token", "no-check"));

        HttpEntity multipartEntity = multipartEntity(attachmentFileName, attachmentContent);
        attachmentPostRequest.setEntity(multipartEntity);

        return attachmentPostRequest;
    }

    HttpPost updateAttachmentContentRequest(String contentId, String attachmentId, InputStream attachmentContent) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(attachmentId), "attachmentId");
        assertMandatoryParameter(attachmentContent != null, "attachmentContent");

        HttpPost attachmentPostRequest = new HttpPost(this.confluenceRestApiEndpoint + "/content/" + contentId + "/child/attachment/" + attachmentId + "/data");
        attachmentPostRequest.addHeader(new BasicHeader("X-Atlassian-Token", "no-check"));

        HttpEntity multipartEntity = multipartEntity(null, attachmentContent);
        attachmentPostRequest.setEntity(multipartEntity);

        return attachmentPostRequest;
    }

    HttpDelete deleteAttachmentRequest(String attachmentId) {
        assertMandatoryParameter(isNotBlank(attachmentId), "attachmentId");

        return new HttpDelete(this.confluenceRestApiEndpoint + "/content/" + attachmentId);
    }

    HttpGet getPageByTitleRequest(String spaceKey, String title) {
        assertMandatoryParameter(isNotBlank(spaceKey), "spaceKey");
        assertMandatoryParameter(isNotBlank(title), "title");

        String encodedTitle = encodeOrThrow(title, "title");

        String searchQuery = this.confluenceRestApiEndpoint + "/content?spaceKey=" + spaceKey + "&title=" + encodedTitle;

        return new HttpGet(searchQuery);
    }

    private String encodeOrThrow(String value, String kind) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not encode "+kind, e);
        }
    }

    public HttpGet findBlogPost(String spaceKey, String title, LocalDate publishDate) {
        String encodedTitle = encodeOrThrow(title, "title");
        String searchQuery = this.confluenceRestApiEndpoint + "/content?type=blogpost&spaceKey=" + spaceKey + "&title=" + encodedTitle
                + "&postingDay="+publishDate.toString();

        return new HttpGet(searchQuery);
    }

    HttpGet getAttachmentByFileNameRequest(String contentId, String attachmentFileName, String expandOptions) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(attachmentFileName), "attachmentFileName");

        URIBuilder uriBuilder = createUriBuilder(this.confluenceRestApiEndpoint + "/content/" + contentId + "/child/attachment");
        uriBuilder.addParameter("filename", attachmentFileName);

        if (isNotBlank(expandOptions)) {
            uriBuilder.addParameter("expand", expandOptions);
        }

        HttpGet getAttachmentByFileNameRequest;
        try {
            getAttachmentByFileNameRequest = new HttpGet(uriBuilder.build().toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL", e);
        }

        return getAttachmentByFileNameRequest;
    }

    private static URIBuilder createUriBuilder(String path) {
        try {
            return new URIBuilder(path);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to parse path as URI: " + path, e);
        }
    }

    HttpGet getPageByIdRequest(String contentId, final String expandOptions) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");

        return new HttpGet(this.confluenceRestApiEndpoint + "/content/" + contentId + "?expand=" + expandOptions);
    }

    HttpGet getChildPagesByIdRequest(String parentContentId, Integer limit, Integer start, String expandOptions) {
        assertMandatoryParameter(isNotBlank(parentContentId), "parentContentId");
        URIBuilder uriBuilder = createUriBuilder(this.confluenceRestApiEndpoint + "/content/" + parentContentId + "/child/page");

        if (limit != null) {
            uriBuilder.addParameter("limit", limit.toString());
        }
        if (start != null) {
            uriBuilder.addParameter("start", start.toString());
        }
        if (isNotBlank(expandOptions)) {
            uriBuilder.addParameter("expand", expandOptions);
        }

        HttpGet getChildPagesByIdRequest;
        try {
            getChildPagesByIdRequest = new HttpGet(uriBuilder.build().toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL", e);
        }

        return getChildPagesByIdRequest;
    }

    public HttpGet getAttachmentsRequest(String contentId, Integer limit, Integer start, String expandOptions) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        URIBuilder uriBuilder = createUriBuilder(this.confluenceRestApiEndpoint + "/content/" + contentId + "/child/attachment");

        if (limit != null) {
            uriBuilder.addParameter("limit", limit.toString());
        }
        if (start != null) {
            uriBuilder.addParameter("start", start.toString());
        }
        if (isNotBlank(expandOptions)) {
            uriBuilder.addParameter("expand", expandOptions);
        }

        HttpGet getAttachmentsRequest;
        try {
            getAttachmentsRequest = new HttpGet(uriBuilder.build().toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL", e);
        }

        return getAttachmentsRequest;
    }

    public HttpGet getAttachmentContentRequest(String relativeDownloadLink) {
        assertMandatoryParameter(isNotBlank(relativeDownloadLink), "relativeDownloadLink");

        return new HttpGet(this.rootConfluenceUrl + relativeDownloadLink);
    }

    public HttpGet getPropertyByKeyRequest(String contentId, String key) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(key), "key");

        return new HttpGet(this.confluenceRestApiEndpoint + "/content/" + contentId + "/property/" + key + "?expand=value");
    }

    public HttpDelete deletePropertyByKeyRequest(String contentId, String key) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(key), "key");

        return new HttpDelete(this.confluenceRestApiEndpoint + "/content/" + contentId + "/property/" + key);
    }

    public HttpPost setPropertyByKeyRequest(String contentId, String key, String value) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(key), "key");
        assertMandatoryParameter(isNotBlank(value), "value");

        PropertyPayload propertyPayload = new PropertyPayload();
        propertyPayload.setKey(key);
        propertyPayload.setValue(value);

        HttpPost postRequest = new HttpPost(this.confluenceRestApiEndpoint + "/content/" + contentId + "/property");
        postRequest.setEntity(httpEntityWithJsonPayload(propertyPayload));
        postRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        return postRequest;
    }

    HttpGet getLabelsRequest(String contentId) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");

        return new HttpGet(this.confluenceRestApiEndpoint + "/content/" + contentId + "/label");
    }

    HttpPost addLabelsRequest(String contentId, List<String> labels) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(!labels.isEmpty(), "labels");

        List<Label> payload = labels.stream().map(Label::new).collect(toList());

        HttpPost addLabelsRequest = new HttpPost(this.confluenceRestApiEndpoint + "/content/" + contentId + "/label");
        addLabelsRequest.setEntity(httpEntityWithJsonPayload(payload));
        addLabelsRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        return addLabelsRequest;
    }

    HttpDelete deleteLabelRequest(String contentId, String label) {
        assertMandatoryParameter(isNotBlank(contentId), "contentId");
        assertMandatoryParameter(isNotBlank(label), "label");

        return new HttpDelete(this.confluenceRestApiEndpoint + "/content/" + contentId + "/label?name=" + label);
    }

    private static HttpPost addPageHttpPost(String confluenceRestApiEndpoint, PageContent pagePayload) {
        HttpPost postRequest = new HttpPost(confluenceRestApiEndpoint + "/content");
        postRequest.setEntity(httpEntityWithJsonPayload(pagePayload));
        postRequest.addHeader(APPLICATION_JSON_UTF8_HEADER);

        return postRequest;
    }

    private static BasicHttpEntity httpEntityWithJsonPayload(Object payload) {
        String jsonPayload = toJsonString(payload);
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(jsonPayload.getBytes(UTF_8)));

        return entity;
    }

    private static String toJsonString(Object objectToConvert) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

            return objectMapper.writeValueAsString(objectToConvert);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error while converting object to JSON", e);
        }
    }

    private static HttpEntity multipartEntity(String attachmentFileName, InputStream attachmentContent) {
        MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
        multipartEntityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        multipartEntityBuilder.setCharset(Charset.forName("UTF-8"));

        InputStreamBody inputStreamBody;
        if (isNotBlank(attachmentFileName)) {
            inputStreamBody = new InputStreamBody(attachmentContent, APPLICATION_OCTET_STREAM, attachmentFileName);
        } else {
            inputStreamBody = new InputStreamBody(attachmentContent, APPLICATION_OCTET_STREAM);
        }

        multipartEntityBuilder.addPart("file", inputStreamBody);

        return multipartEntityBuilder.build();
    }


    static class PagePayloadBuilder {

        private String title;
        private String content;
        private String spaceKey;
        private String ancestorId;
        private Integer version;
        private String versionMessage;
        private LocalDate publishDate;

        public PagePayloadBuilder title(String title) {
            this.title = title;

            return this;
        }

        public PagePayloadBuilder content(String content) {
            this.content = content;

            return this;
        }

        public PagePayloadBuilder spaceKey(String spaceKey) {
            this.spaceKey = spaceKey;

            return this;
        }

        public PagePayloadBuilder ancestorId(String ancestorId) {
            this.ancestorId = ancestorId;

            return this;
        }

        public PagePayloadBuilder version(Integer version) {
            this.version = version;

            return this;
        }

        public PagePayloadBuilder versionMessage(String versionMessage) {
            this.versionMessage = versionMessage;

            return this;
        }

        public PagePayloadBuilder publishDate(LocalDate publishDate) {
            this.publishDate = publishDate;

            return this;
        }

        private PagePayload build() {
            PagePayload pagePayload = new PagePayload();
            pagePayload.setTitle(this.title);

            pagePayload.setBody(createBody());
            pagePayload.setSpace(createSpace());
            pagePayload.setVersion(createVersion());

            if (isNotBlank(this.ancestorId)) {
                Ancestor ancestor = new Ancestor();
                ancestor.setId(this.ancestorId);
                pagePayload.addAncestor(ancestor);
            }

            return pagePayload;
        }

        private BlogPostPayload buildPost() {
            Storage storage = new Storage();
            storage.setValue(this.content);

            BlogPostPayload pagePayload = new BlogPostPayload();
            pagePayload.setTitle(this.title);
            pagePayload.setBody(createBody());
            pagePayload.setSpace(createSpace());
            pagePayload.setVersion(createVersion());

            if (publishDate != null){
                CreationInfo info = new CreationInfo();
                info.setCreatedDate(ZonedDateTime.of(publishDate, LocalTime.NOON, ZoneId.of("Z")));
                pagePayload.setHistory(info);
            }

            return pagePayload;
        }

        private Body createBody() {
            Storage storage = new Storage();
            storage.setValue(this.content);

            Body body = new Body();
            body.setStorage(storage);
            return body;
        }

        private Space createSpace() {
            if (isNotBlank(this.spaceKey)) {
                Space space = new Space();
                space.setKey(this.spaceKey);
                return space;
            } else {
                return null;
            }
        }

        private Version createVersion() {
            if (this.version != null) {
                Version versionContainer = new Version();
                versionContainer.setNumber(this.version);
                if (this.versionMessage != null) {
                    versionContainer.setMessage(this.versionMessage);
                }
                return versionContainer;
            } else {
                return null;
            }
        }

        static PagePayloadBuilder pagePayloadBuilder() {
            return new PagePayloadBuilder();
        }
    }

}
