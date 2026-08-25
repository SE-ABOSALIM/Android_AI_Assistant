package com.example.anroidaiassistant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class PackageVisibilityManifestTest {
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES";

    @Test
    public void mergedManifest_doesNotRequestQueryAllPackages() throws Exception {
        Element manifest = mergedDebugManifest();

        assertFalse(hasNamedElement(manifest, "uses-permission", QUERY_ALL_PACKAGES));
    }

    @Test
    public void launcherQueriesDeclaration_remainsPresent() throws Exception {
        Element manifest = mergedDebugManifest();

        assertTrue(hasIntentQuery(
                manifest,
                "android.intent.action.MAIN",
                "android.intent.category.LAUNCHER",
                null
        ));
    }

    @Test
    public void phoneHandlerQueries_areBoundedToTelIntents() throws Exception {
        Element manifest = mergedDebugManifest();

        assertTrue(hasIntentQuery(manifest, "android.intent.action.CALL", null, "tel"));
        assertTrue(hasIntentQuery(manifest, "android.intent.action.DIAL", null, "tel"));
    }

    private Element mergedDebugManifest() throws Exception {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path moduleRoot = Files.isDirectory(workingDirectory.resolve("src/main"))
                ? workingDirectory
                : workingDirectory.resolve("app");
        Path manifestPath = moduleRoot.resolve(
                "build/intermediates/merged_manifest/debug/"
                        + "processDebugMainManifest/AndroidManifest.xml"
        );
        assertTrue("Merged debug manifest is missing: " + manifestPath, Files.isRegularFile(manifestPath));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(manifestPath.toFile()).getDocumentElement();
    }

    private boolean hasNamedElement(Element manifest, String tagName, String expectedName) {
        NodeList nodes = manifest.getElementsByTagName(tagName);
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (expectedName.equals(element.getAttributeNS(ANDROID_NAMESPACE, "name"))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIntentQuery(
            Element manifest,
            String expectedAction,
            String expectedCategory,
            String expectedScheme
    ) {
        NodeList queries = manifest.getElementsByTagName("queries");
        for (int queryIndex = 0; queryIndex < queries.getLength(); queryIndex++) {
            NodeList children = queries.item(queryIndex).getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                Node child = children.item(childIndex);
                if (child instanceof Element && "intent".equals(child.getNodeName())) {
                    Element intent = (Element) child;
                    if (hasNamedChild(intent, "action", "name", expectedAction)
                            && (expectedCategory == null
                            || hasNamedChild(intent, "category", "name", expectedCategory))
                            && (expectedScheme == null
                            || hasNamedChild(intent, "data", "scheme", expectedScheme))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasNamedChild(
            Element parent,
            String tagName,
            String attributeName,
            String expectedValue
    ) {
        NodeList children = parent.getElementsByTagName(tagName);
        for (int index = 0; index < children.getLength(); index++) {
            Element child = (Element) children.item(index);
            if (expectedValue.equals(child.getAttributeNS(ANDROID_NAMESPACE, attributeName))) {
                return true;
            }
        }
        return false;
    }
}
