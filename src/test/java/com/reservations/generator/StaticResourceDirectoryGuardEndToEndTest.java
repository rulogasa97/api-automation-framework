package com.reservations.generator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the design's threat-matrix item (d) ("static resources serve only
 * /css, /js, /images — no directory listing, default Boot handler only")
 * against a REAL running server, not MockMvc.
 *
 * <p>{@code SpringBootMockServletContext} (used under {@code @AutoConfigureMockMvc})
 * resolves a bare directory path differently from a real embedded Tomcat —
 * confirmed by direct comparison during this phase's implementation: MockMvc
 * returned 200 with the directory's single filename as the body for {@code
 * GET /css/}, while a real running server correctly 404s. Testing the
 * directory-listing guard here, against the real server, is what actually
 * proves production behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaticResourceDirectoryGuardEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void aBareStaticDirectoryPathNeverReturnsAListing() {
        ResponseEntity<String> response = restTemplate.getForEntity("/css/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Triangulation: an actually-registered file under the same directory
     * IS served, so the 404 above is a real "not found", not evidence that
     * {@code /css/**} is broken entirely.
     */
    @Test
    void aRealFileUnderTheSameDirectoryIsServed() {
        ResponseEntity<String> response = restTemplate.getForEntity("/css/united.css", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("ual-header");
    }
}
