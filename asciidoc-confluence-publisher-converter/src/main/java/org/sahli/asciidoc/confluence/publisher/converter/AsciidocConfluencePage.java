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

package org.sahli.asciidoc.confluence.publisher.converter;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider.AsciidocPage;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.newInputStream;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.regex.Matcher.quoteReplacement;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringEscapeUtils.unescapeHtml;
import static org.asciidoctor.Asciidoctor.Factory.create;
import static org.asciidoctor.SafeMode.UNSAFE;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class AsciidocConfluencePage {

    private static final Pattern CDATA_PATTERN = compile("<!\\[CDATA\\[.*?\\]\\]>", DOTALL);
    private static final Pattern ATTACHMENT_PATH_PATTERN = compile("<ri:attachment ri:filename=\"(.*?)\"");
    private static final Pattern PAGE_TITLE_PATTERN = compile("<ri:page ri:content-title=\"(.*?)\"");

    private static final Asciidoctor ASCIIDOCTOR = create();

    static {
        ASCIIDOCTOR.requireLibrary("asciidoctor-diagram");
    }

    private final String pageTitle;
    private final String htmlContent;
    private final Map<String, String> attachments;
    private final List<String> keywords;

    private AsciidocConfluencePage(String pageTitle, String htmlContent, Map<String, String> attachments, List<String> keywords) {
        this.pageTitle = pageTitle;
        this.htmlContent = htmlContent;
        this.attachments = attachments;
        this.keywords = keywords;
    }

    public String content() {
        return this.htmlContent;
    }

    public String pageTitle() {
        return this.pageTitle;
    }

    public Map<String, String> attachments() {
        return unmodifiableMap(this.attachments);
    }

    public List<String> keywords() {
        return unmodifiableList(this.keywords);
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(AsciidocPage asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder) {
        return newAsciidocConfluencePage(asciidocPage, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), emptyMap());
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(AsciidocPage asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, Map<String, Object> userAttributes) {
        return newAsciidocConfluencePage(asciidocPage, sourceEncoding, templatesDir, pageAssetsFolder, new NoOpPageTitlePostProcessor(), userAttributes);
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(AsciidocPage asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor) {
        return newAsciidocConfluencePage(asciidocPage, sourceEncoding, templatesDir, pageAssetsFolder, pageTitlePostProcessor, emptyMap());
    }

    public static AsciidocConfluencePage newAsciidocConfluencePage(AsciidocPage asciidocPage, Charset sourceEncoding, Path templatesDir, Path pageAssetsFolder, PageTitlePostProcessor pageTitlePostProcessor, Map<String, Object> userAttributes) {
        try {
            Path asciidocPagePath = asciidocPage.path();
            String asciidocContent = readIntoString(newInputStream(asciidocPagePath), sourceEncoding);

            Map<String, String> attachmentCollector = new HashMap<>();

            Options options = options(templatesDir, asciidocPagePath.getParent(), pageAssetsFolder, userAttributes);
            String pageContent = convertedContent(asciidocContent, options, asciidocPagePath, attachmentCollector, pageTitlePostProcessor, sourceEncoding);

            String pageTitle = pageTitle(asciidocContent, pageTitlePostProcessor, asciidocPagePath.getFileName().toString());

            List<String> keywords = keywords(asciidocContent);

            return new AsciidocConfluencePage(pageTitle, pageContent, attachmentCollector, keywords);
        } catch (IOException e) {
            throw new RuntimeException("Could not create asciidoc confluence page", e);
        }
    }

    private static String deriveAttachmentName(String path) {
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    private static String convertedContent(String adocContent, Options options, Path pagePath, Map<String, String> attachmentCollector, PageTitlePostProcessor pageTitlePostProcessor, Charset sourceEncoding) {
        String content = ASCIIDOCTOR.convert(adocContent, options);
        String postProcessedContent = postProcessContent(content,
                replaceCrossReferenceTargets(pagePath, pageTitlePostProcessor, sourceEncoding),
                collectAndReplaceAttachmentFileNames(attachmentCollector),
                unescapeCdataHtmlContent()
        );

        return postProcessedContent;
    }

    private static Function<String, String> unescapeCdataHtmlContent() {
        return (content) -> replaceAll(content, CDATA_PATTERN, (matchResult) -> unescapeHtml(matchResult.group()));
    }

    private static Function<String, String> collectAndReplaceAttachmentFileNames(Map<String, String> attachmentCollector) {
        return (content) -> replaceAll(content, ATTACHMENT_PATH_PATTERN, (matchResult) -> {
            String attachmentPath = matchResult.group(1);
            String attachmentFileName = deriveAttachmentName(attachmentPath);

            attachmentCollector.put(attachmentPath, attachmentFileName);

            return "<ri:attachment ri:filename=\"" + attachmentFileName + "\"";
        });
    }

    private static String replaceAll(String content, Pattern pattern, Function<MatchResult, String> replacer) {
        StringBuffer replacedContent = new StringBuffer();
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            matcher.appendReplacement(replacedContent, quoteReplacement(replacer.apply(matcher.toMatchResult())));
        }

        matcher.appendTail(replacedContent);

        return replacedContent.toString();
    }

    @SafeVarargs
    private static String postProcessContent(String initialContent, Function<String, String>... postProcessors) {
        return stream(postProcessors).reduce(initialContent, (accumulator, postProcessor) -> postProcessor.apply(accumulator), unusedCombiner());
    }

    private static String pageTitle(String pageContent, PageTitlePostProcessor pageTitlePostProcessor, String fileName) {
        return Optional.ofNullable(ASCIIDOCTOR.readDocumentHeader(pageContent).getDocumentTitle())
                .map(title -> title.getMain())
                .map((pageTitle) -> pageTitlePostProcessor.process(pageTitle))
                .orElseGet(() -> fileName.substring(0, fileName.lastIndexOf('.')));
    }

    private static Options options(Path templatesFolder, Path baseFolder, Path generatedAssetsTargetFolder, Map<String, Object> userAttributes) {
        if (!exists(templatesFolder)) {
            throw new RuntimeException("templateDir folder does not exist");
        }

        if (!isDirectory(templatesFolder)) {
            throw new RuntimeException("templateDir folder is not a folder");
        }

        Map<String, Object> attributes = new HashMap<>(userAttributes);
        attributes.put("imagesoutdir", generatedAssetsTargetFolder.toString());
        attributes.put("outdir", generatedAssetsTargetFolder.toString());
        attributes.put("source-highlighter", "none");

        return OptionsBuilder.options()
                .backend("xhtml5")
                .safe(UNSAFE)
                .baseDir(baseFolder.toFile())
                .templateDirs(templatesFolder.toFile())
                .attributes(attributes)
                .get();
    }

    private static Function<String, String> replaceCrossReferenceTargets(Path pagePath, PageTitlePostProcessor pageTitlePostProcessor, Charset sourceEncoding) {
        return (content) -> replaceAll(content, PAGE_TITLE_PATTERN, (matchResult) -> {
            String htmlTarget = matchResult.group(1);
            Path referencedPagePath = pagePath.getParent().resolve(Paths.get(htmlTarget.substring(0, htmlTarget.lastIndexOf('.')) + ".adoc"));

            try {
                String referencedPageContent = readIntoString(new FileInputStream(referencedPagePath.toFile()), sourceEncoding);
                String referencedPageTitle = pageTitle(referencedPageContent, pageTitlePostProcessor, referencedPagePath.getFileName().toString());

                return "<ri:page ri:content-title=\"" + referencedPageTitle + "\"";
            } catch (FileNotFoundException e) {
                throw new RuntimeException("unable to find cross-referenced page '" + referencedPagePath + "'", e);
            }
        });
    }

    private static BinaryOperator<String> unusedCombiner() {
        return (a, b) -> a;
    }

    private static String readIntoString(InputStream input, Charset encoding) {
        try {
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input, encoding))) {
                return buffer.lines().collect(joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read file content", e);
        }
    }

    private static List<String> keywords(String pageContent) {
        String keywords = (String) ASCIIDOCTOR.readDocumentHeader(pageContent).getAttributes().get("keywords");
        if (keywords == null) {
            return emptyList();
        }

        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .collect(toList());
    }
}
