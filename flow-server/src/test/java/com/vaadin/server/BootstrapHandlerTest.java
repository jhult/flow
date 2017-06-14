package com.vaadin.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletRequest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.vaadin.annotations.HtmlImport;
import com.vaadin.annotations.JavaScript;
import com.vaadin.annotations.StyleSheet;
import com.vaadin.annotations.Title;
import com.vaadin.external.jsoup.nodes.Document;
import com.vaadin.external.jsoup.nodes.Element;
import com.vaadin.external.jsoup.select.Elements;
import com.vaadin.flow.template.angular.InlineTemplate;
import com.vaadin.server.BootstrapHandler.BootstrapContext;
import com.vaadin.shared.ApplicationConstants;
import com.vaadin.shared.VaadinUriResolver;
import com.vaadin.shared.ui.LoadMode;
import com.vaadin.tests.util.MockDeploymentConfiguration;
import com.vaadin.ui.Html;
import com.vaadin.ui.Text;
import com.vaadin.ui.UI;

public class BootstrapHandlerTest {

    static final String UI_TITLE = "UI_TITLE";

    @Title(UI_TITLE)
    @JavaScript(value = "lazy.js", loadMode = LoadMode.LAZY)
    @StyleSheet(value = "lazy.css", loadMode = LoadMode.LAZY)
    @HtmlImport(value = "lazy.html", loadMode = LoadMode.LAZY)
    @JavaScript("eager.js")
    @StyleSheet("context://eager-relative.css")
    @StyleSheet("eager.css")
    @HtmlImport("eager.html")
    private class TestUI extends UI {

        @Override
        protected void init(VaadinRequest request) {
            super.init(request);
            add(new Html("<div foo=bar>foobar</div>"));
            add(new Text("Hello world"));
            add(new InlineTemplate("<div><script></script></div>"));
        }

    }

    private TestUI testUI;
    private BootstrapContext context;
    private VaadinRequest request;
    private VaadinSession session;
    private VaadinServletService service;
    private MockDeploymentConfiguration deploymentConfiguration;

    @Before
    public void setup() {
        BootstrapHandler.clientEngineFile = "foobar";
        testUI = new TestUI();

        deploymentConfiguration = new MockDeploymentConfiguration();

        service = Mockito.spy(new MockVaadinServletService(new VaadinServlet(),
                deploymentConfiguration));

        session = Mockito.spy(new MockVaadinSession(service));
        session.lock();
        session.setConfiguration(deploymentConfiguration);
        testUI.getInternals().setSession(session);
    }

    private void initUI(UI ui) {
        initUI(ui, createVaadinRequest());
    }

    private void initUI(UI ui, VaadinRequest request) {
        this.request = request;
        try {
            service.init();
        } catch (ServiceException e) {
            throw new RuntimeException("Error initializing the VaadinService",
                    e);
        }
        ui.doInit(request, 0);
        context = new BootstrapContext(request, null, session, ui);
    }

    @Test
    public void testInitialPageTitle_pageSetTitle_noExecuteJavascript() {
        initUI(testUI, createVaadinRequest());
        String overriddenPageTitle = "overridden";
        testUI.getPage().setTitle(overriddenPageTitle);

        assertEquals(overriddenPageTitle,
                BootstrapHandler.resolvePageTitle(context).get());

        assertEquals(0, testUI.getInternals().dumpPendingJavaScriptInvocations()
                .size());
    }

    @Test
    public void testInitialPageTitle_nullTitle_noTitle() {
        initUI(testUI, createVaadinRequest());
        assertFalse(BootstrapHandler.resolvePageTitle(context).isPresent());
    }

    @Test
    public void renderUI() throws IOException {
        TestUI anotherUI = new TestUI();
        initUI(testUI);
        anotherUI.getInternals().setSession(session);
        VaadinRequest vaadinRequest = createVaadinRequest();
        anotherUI.doInit(vaadinRequest, 0);
        BootstrapContext bootstrapContext = new BootstrapContext(vaadinRequest,
                null, session, anotherUI);

        Document page = BootstrapHandler.getBootstrapPage(bootstrapContext);
        Element body = page.body();

        assertEquals(1, body.childNodeSize());
        assertEquals("noscript", body.child(0).tagName());
    }

    @Test // #1134
    public void testBody() throws Exception {
        initUI(testUI, createVaadinRequest());

        Document page = BootstrapHandler.getBootstrapPage(
                new BootstrapContext(request, null, session, testUI));

        Element body = page.head().nextElementSibling();

        assertEquals("body", body.tagName());
        assertEquals("html", body.parent().tagName());
        assertEquals(2, body.parent().childNodeSize());
    }

    @Test
    public void testBootstrapListener() {
        List<BootstrapListener> listeners = new ArrayList<>(3);
        AtomicReference<VaadinUriResolver> resolver = new AtomicReference<>();
        listeners.add(evt -> evt.getDocument().head().getElementsByTag("script")
                .remove());
        listeners.add(evt -> {
            resolver.set(evt.getUriResolver());
            evt.getDocument().head().appendElement("script").attr("src",
                    "testing.1");
        });
        listeners.add(evt -> evt.getDocument().head().appendElement("script")
                .attr("src", "testing.2"));

        Mockito.when(service.processBootstrapListeners(Mockito.anyList()))
                .thenReturn(listeners);

        initUI(testUI);

        BootstrapContext bootstrapContext = new BootstrapContext(request, null,
                session, testUI);
        Document page = BootstrapHandler.getBootstrapPage(bootstrapContext);

        Elements scripts = page.head().getElementsByTag("script");
        assertEquals(2, scripts.size());
        assertEquals("testing.1", scripts.get(0).attr("src"));
        assertEquals("testing.2", scripts.get(1).attr("src"));

        Assert.assertNotNull(resolver.get());
        Assert.assertEquals(bootstrapContext.getUriResolver(), resolver.get());

        Mockito.verify(service).processBootstrapListeners(Mockito.anyList());
    }

    @Test
    public void frontendProtocol_productionMode_useDifferentUrlsForEs5AndEs6() {
        initUI(testUI);
        deploymentConfiguration.setProductionMode(true);
        WebBrowser mockedWebBrowser = Mockito.mock(WebBrowser.class);
        Mockito.when(session.getBrowser()).thenReturn(mockedWebBrowser);

        Mockito.when(mockedWebBrowser.isEs6Supported()).thenReturn(true);

        String resolvedContext = context.getUriResolver()
                .resolveVaadinUri(ApplicationConstants.CONTEXT_PROTOCOL_PREFIX);

        String urlES6 = context.getUriResolver().resolveVaadinUri(
                ApplicationConstants.FRONTEND_PROTOCOL_PREFIX + "foo");

        assertEquals(Constants.FRONTEND_URL_ES6_DEFAULT_VALUE
                .replace(ApplicationConstants.CONTEXT_PROTOCOL_PREFIX,
                        resolvedContext)
                + "foo", urlES6);

        Mockito.when(mockedWebBrowser.isEs6Supported()).thenReturn(false);

        String urlES5 = context.getUriResolver().resolveVaadinUri(
                ApplicationConstants.FRONTEND_PROTOCOL_PREFIX + "foo");

        assertEquals(Constants.FRONTEND_URL_ES5_DEFAULT_VALUE
                .replace(ApplicationConstants.CONTEXT_PROTOCOL_PREFIX,
                        resolvedContext)
                + "foo", urlES5);

        Mockito.verify(session, Mockito.times(3)).getBrowser();
    }

    @Test
    public void frontendProtocol_notInProductionMode_useContext() {
        initUI(testUI);
        deploymentConfiguration.setProductionMode(false);
        WebBrowser mockedWebBrowser = Mockito.mock(WebBrowser.class);
        Mockito.when(session.getBrowser()).thenReturn(mockedWebBrowser);

        Mockito.when(mockedWebBrowser.isEs6Supported()).thenReturn(true);

        String resolvedContext = context.getUriResolver()
                .resolveVaadinUri(ApplicationConstants.CONTEXT_PROTOCOL_PREFIX);

        String urlES6 = context.getUriResolver().resolveVaadinUri(
                ApplicationConstants.FRONTEND_PROTOCOL_PREFIX + "foo");

        assertEquals(resolvedContext + "foo", urlES6);

        Mockito.when(mockedWebBrowser.isEs6Supported()).thenReturn(false);

        String urlES5 = context.getUriResolver().resolveVaadinUri(
                ApplicationConstants.FRONTEND_PROTOCOL_PREFIX + "foo");

        assertEquals(resolvedContext + "foo", urlES5);

        Mockito.verify(session, Mockito.times(3)).getBrowser();
    }

    @Test
    public void frontendProtocol_notInProductionModeAndWithProperties_useProperties() {
        initUI(testUI);
        deploymentConfiguration.setProductionMode(false);
        WebBrowser mockedWebBrowser = Mockito.mock(WebBrowser.class);
        Mockito.when(session.getBrowser()).thenReturn(mockedWebBrowser);

        deploymentConfiguration.setApplicationOrSystemProperty(
                Constants.FRONTEND_URL_ES6, "bar/es6/");
        deploymentConfiguration.setApplicationOrSystemProperty(
                Constants.FRONTEND_URL_ES5, "bar/es5/");

        Mockito.when(mockedWebBrowser.isEs6Supported()).thenReturn(true);

        String urlES6 = context.getUriResolver().resolveVaadinUri(
                ApplicationConstants.FRONTEND_PROTOCOL_PREFIX + "foo");

        assertEquals("bar/es6/foo", urlES6);

        Mockito.when(mockedWebBrowser.isEs6Supported()).thenReturn(false);

        String urlES5 = context.getUriResolver().resolveVaadinUri(
                ApplicationConstants.FRONTEND_PROTOCOL_PREFIX + "foo");

        assertEquals("bar/es5/foo", urlES5);

        Mockito.verify(session, Mockito.times(3)).getBrowser();
    }

    private VaadinRequest createVaadinRequest() {
        HttpServletRequest request = createRequest();
        return new VaadinServletRequest(request, service);
    }

    private HttpServletRequest createRequest() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.doAnswer(invocation -> "").when(request).getServletPath();
        return request;
    }

    private static void verifyMeterElement(Element meter) {
        assertEquals("meter", meter.tagName());
        assertEquals("foo", meter.className());
        assertEquals("1000", meter.attr("max"));
        assertEquals("500", meter.attr("value"));
    }

    private static com.vaadin.flow.dom.Element createMeterElement() {
        com.vaadin.flow.dom.Element meter = new com.vaadin.flow.dom.Element(
                "meter");
        meter.getStyle().set("color", "black");
        meter.setAttribute("max", "1000");
        meter.setAttribute("value", "500");
        meter.getClassList().add("foo");
        return meter;
    }
}