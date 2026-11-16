package job.search.kg.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class YouTubeTest {

    @Test
    void parsesCommonLinks() {
        String id = "dQw4w9WgXcQ";
        assertEquals(id, YouTube.videoId("https://www.youtube.com/watch?v=" + id));
        assertEquals(id, YouTube.videoId("https://youtube.com/watch?feature=share&v=" + id + "&t=10"));
        assertEquals(id, YouTube.videoId("https://youtu.be/" + id + "?si=abc"));
        assertEquals(id, YouTube.videoId("https://www.youtube.com/shorts/" + id));
        assertEquals(id, YouTube.videoId("https://www.youtube-nocookie.com/embed/" + id));
        assertEquals(id, YouTube.videoId("https://m.youtube.com/live/" + id));
    }

    @Test
    void rejectsOtherLinks() {
        assertNull(YouTube.videoId("https://vimeo.com/123456"));
        assertNull(YouTube.videoId("https://www.youtube.com/@workkg"));
        assertNull(YouTube.videoId(null));
    }
}
