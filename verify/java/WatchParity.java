import app.dayhub.data.StopwatchMath;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class WatchParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  java: " + got + "\n  web:  " + want);
            System.exit(1);
        }
    }

    static void near(double got, double want, String what) {
        checks++;
        if (!(got == want || Math.abs(got - want) < 1e-9)) {
            System.out.println("MISMATCH " + what + "\n  java: " + got + "\n  web:  " + want);
            System.exit(1);
        }
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));

        JSONArray clamp = root.getJSONArray("clamp");
        for (int i = 0; i < clamp.length(); i++) {
            JSONObject c = clamp.getJSONObject(i);
            JSONObject in = c.getJSONObject("input");
            Object value;
            switch (in.getString("t")) {
                case "nan": value = Double.NaN; break;
                case "inf": value = Double.POSITIVE_INFINITY; break;
                case "ninf": value = Double.NEGATIVE_INFINITY; break;
                case "n": value = in.getDouble("v"); break;
                default: value = in.getString("v");
            }
            Integer want = c.isNull("expected") ? null : Integer.valueOf(c.getInt("expected"));
            eq(StopwatchMath.clampMinutes(value), want, "clampMinutes(" + in + ")");
        }

        JSONArray plans = root.getJSONArray("plans");
        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.getJSONObject(i);
            double m = p.getDouble("m");
            StopwatchMath.Plan g = StopwatchMath.dialPlan((int) Math.round(m == Math.rint(m) ? m : Math.round(m)));
            JSONObject w = p.getJSONObject("plan");
            String id = "plan for " + m;
            eq(g.span, w.getInt("span"), id + " span");
            eq(g.step, w.getInt("step"), id + " step");
            eq(g.minor, w.getInt("minor"), id + " minor");
            JSONArray ticks = w.getJSONArray("ticks");
            eq(g.ticks.size(), ticks.length(), id + " tick count");
            for (int k = 0; k < ticks.length(); k++) {
                JSONObject t = ticks.getJSONObject(k);
                eq(g.ticks.get(k).at, t.getInt("at"), id + " tick " + k + " at");
                near(g.ticks.get(k).deg, t.getDouble("deg"), id + " tick " + k + " deg");
                eq(g.ticks.get(k).major, t.getBoolean("major"), id + " tick " + k + " major");
            }
            JSONArray labels = w.getJSONArray("labels");
            eq(g.labels.size(), labels.length(), id + " label count");
            for (int k = 0; k < labels.length(); k++) {
                JSONObject l = labels.getJSONObject(k);
                eq(g.labels.get(k).at, l.getInt("at"), id + " label " + k + " at");
                near(g.labels.get(k).deg, l.getDouble("deg"), id + " label " + k + " deg");
                eq(g.labels.get(k).text, l.getString("text"), id + " label " + k + " text");
            }
        }

        JSONArray labels = root.getJSONArray("labels");
        for (int i = 0; i < labels.length(); i++) {
            JSONObject l = labels.getJSONObject(i);
            double m = l.getDouble("m");
            eq(StopwatchMath.minutesLabel(m), l.getString("label"), "minutesLabel(" + m + ")");
            eq(StopwatchMath.faceLength(m), l.getString("face"), "faceLength(" + m + ")");
        }

        JSONArray points = root.getJSONArray("points");
        for (int i = 0; i < points.length(); i++) {
            JSONObject p = points.getJSONObject(i);
            double[] g = StopwatchMath.polar(p.getDouble("cx"), p.getDouble("cy"), p.getDouble("r"), p.getDouble("deg"));
            near(g[0], p.getJSONObject("p").getDouble("x"), "polar x " + p);
            near(g[1], p.getJSONObject("p").getDouble("y"), "polar y " + p);
        }

        JSONArray wedges = root.getJSONArray("wedges");
        for (int i = 0; i < wedges.length(); i++) {
            JSONObject w = wedges.getJSONObject(i);
            double cx = w.has("cx") ? w.getDouble("cx") : 120, cy = w.has("cy") ? w.getDouble("cy") : 148, r = w.has("r") ? w.getDouble("r") : 86;
            eq(StopwatchMath.wedgePath(cx, cy, r, w.getDouble("from"), w.getDouble("to")), w.getString("path"), "wedge " + w);
        }

        JSONArray hands = root.getJSONArray("hands");
        for (int i = 0; i < hands.length(); i++) {
            JSONObject h = hands.getJSONObject(i);
            near(StopwatchMath.handDegrees(h.getDouble("el"), h.getDouble("total")), h.getDouble("deg"), "hand " + h);
        }
        System.out.println("IDENTICAL to the web app: " + clamp.length() + " length inputs, " + plans.length() + " dials, " + labels.length()
                + " labels, " + points.length() + " points, " + wedges.length() + " wedges, " + hands.length() + " hand angles (" + checks + " comparisons)");
    }
}
