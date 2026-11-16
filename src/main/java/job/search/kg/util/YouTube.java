package job.search.kg.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ссылки YouTube: watch?v=, youtu.be/, shorts/, embed/, live/ → id видео (11 символов) */
public final class YouTube {

    private static final Pattern ID = Pattern.compile(
            "(?:youtube(?:-nocookie)?\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/|v/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    private YouTube() {
    }

    /** null — не ссылка на видео YouTube */
    public static String videoId(String url) {
        if (url == null) return null;
        Matcher m = ID.matcher(url.trim());
        return m.find() ? m.group(1) : null;
    }

    public static String thumbnail(String id) {
        return id == null ? null : "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
    }
}
