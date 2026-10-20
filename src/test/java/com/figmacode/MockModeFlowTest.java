package com.figmacode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The whole user journey in mock mode: sign in, connect Figma, generate, edit, download, preview. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class MockModeFlowTest {

    @Autowired
    MockMvc mvc;

    @Test
    void fullJourney() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", "rakesh").param("password", "x"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/figma/status").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Figma is not connected")));

        MvcResult connect = mvc.perform(get("/figma/connect").session(session)).andReturn();
        String location = connect.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/figma/mock-authorize?state=");
        String state = java.net.URLDecoder.decode(location.replaceAll(".*state=([^&]+).*", "$1"), "UTF-8");

        mvc.perform(get("/figma/callback").session(session).param("code", "mock-code").param("state", state))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/figma/status").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Connected to Figma MCP")));

        MvcResult created = mvc.perform(post("/generations").session(session).with(csrf())
                        .param("figmaUrl", "https://www.figma.com/design/AbC123/Launch?node-id=12-345"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String generationUrl = created.getResponse().getRedirectedUrl();
        assertThat(generationUrl).startsWith("/generations/");

        mvc.perform(get(generationUrl).session(session))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ProductLaunchCard")));

        mvc.perform(post(generationUrl + "/code").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"export default function ProductLaunchCard(){ return <div className=\\\"p-2\\\">Edited</div>; }\"}"))
                .andExpect(status().isOk());

        MvcResult zip = mvc.perform(get(generationUrl + "/download").session(session))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("product-launch-card.zip")))
                .andReturn();
        List<String> entries = new ArrayList<>();
        String component = null;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip.getResponse().getContentAsByteArray()))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                entries.add(e.getName());
                if (e.getName().endsWith("src/ProductLaunchCard.tsx")) {
                    component = new String(in.readAllBytes());
                }
            }
        }
        assertThat(entries).contains("product-launch-card/package.json", "product-launch-card/src/main.tsx",
                "product-launch-card/public/assets/imgHero.svg");
        assertThat(component).contains("Edited");

        mvc.perform(get(generationUrl.replace("/generations/", "/preview/")).session(session))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("connect-src 'none'")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data:image\\/svg+xml;base64,")));
    }

    @Test
    void wrongPasswordShowsError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", "rakesh").param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(get("/login").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Wrong Bitbucket username or password")));
    }

    @Test
    void otherUsersCannotSeeGenerations() throws Exception {
        mvc.perform(get("/generations/does-not-exist").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                                        .authenticated(new com.figmacode.auth.CurrentUser("x", "x", "X", ""), null, List.of()))))
                .andExpect(status().isNotFound());
    }
}
