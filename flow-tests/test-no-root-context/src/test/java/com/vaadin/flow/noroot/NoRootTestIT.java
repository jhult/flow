package com.vaadin.flow.noroot;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.By;

import com.vaadin.flow.testutil.ChromeBrowserTest;

import static org.junit.Assert.assertTrue;

public class NoRootTestIT extends ChromeBrowserTest {

    @Test
    public void filesLoadedCorrectlyForServletWithNonRootMapping() {
        open();

        checkLogsForErrors();

        String polymerVersionProperty = "polymer.version";
        String expectedPolymerVersion = System
                .getProperty(polymerVersionProperty);
        assertTrue(String.format(
                "System property '%s' is empty, double check the project pom",
                polymerVersionProperty),
                expectedPolymerVersion != null
                        && !expectedPolymerVersion.isEmpty());

        String actualPolymerText = findElement(
                By.id(NoRootTestView.TEST_VIEW_ID)).getText();
        assertTrue(
                String.format("Expected Polymer version: '%s', actual: '%s'",
                        expectedPolymerVersion, actualPolymerText),
                actualPolymerText.endsWith(expectedPolymerVersion));

        // Refresh the page to get the service worker activated
        getDriver().get(getDriver().getCurrentUrl());
        @SuppressWarnings("unchecked") // javascript
        Map<String, String> serviceWorkerData = (Map<String, String>) executeScript(
                "return navigator.serviceWorker.controller");
        Assert.assertNotNull(
                "Expect to have service worker data after page reload",
                serviceWorkerData);
        Assert.assertEquals(
                "Expect the service worker to be activated after the page reload",
                "activated", serviceWorkerData.get("state"));
        checkLogsForErrors();
    }

    @Override
    protected String getTestPath() {
        return "/context/custom/";
    }
}
