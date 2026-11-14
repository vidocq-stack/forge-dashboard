package io.vidocq.tools.dashboard.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextPageTest {

    @Test
    void linkHeaderWithNextHasNext() {
        assertTrue(NextPage.hasNext("<https://codefloe.com/api/v1/orgs/Vidocq/repos?page=2&limit=50>; rel=\"next\","
                + "<https://codefloe.com/api/v1/orgs/Vidocq/repos?page=3&limit=50>; rel=\"last\""));
        assertFalse(NextPage.hasNext(null));
        assertFalse(NextPage.hasNext(""));
        assertFalse(NextPage.hasNext("<https://codefloe.com/api/v1/orgs/Vidocq/repos?page=1>; rel=\"prev\""));
    }

    @Test
    void lastPageHasOnlyFirstAndPrev() {
        assertFalse(NextPage.hasNext("<https://x/?page=1>; rel=\"first\",<https://x/?page=2>; rel=\"prev\""));
    }
}
