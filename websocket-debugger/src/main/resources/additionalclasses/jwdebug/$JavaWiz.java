package jwdebug;
import java.util.stream.Collector;
import java.util.Map;
import java.util.Iterator;
import java.util.Collection;
import java.util.stream.Collectors;

public class $JavaWiz {
    public static boolean recordCondition(boolean value, int conditionId) {
        return value;
    }

    public static int recordArrayAccess(Object array, int index, int arrayAccessId, int dimension) {
        return index;
    }

    public static void traceStream(String direction, Object elem, String name, int id, int streamId, String param) {}
    public static void traceStream(String direction, Object elem, String name, int id, int streamId, String param, String value) {}
    public static void traceStream(String direction, String elem, String name, int id, int streamId, String param) {}
    public static void traceStream(String direction, int elem, String name, int id, int streamId, String param) {}
    public static void traceStream(String direction, long elem, String name, int id, int streamId, String param) {}
    public static void traceStream(String direction, double elem, String name, int id, int streamId, String param) {}
    public static void traceStream(String direction, float elem, String name, int id, int streamId, String param) {}
    public static void traceStream(String direction, char elem, String name, int id, int streamId, String param) {}
    public static void traceStream(String direction, byte elem, String name, int id, int streamId, String param) {}
    public static void traceStream(String direction, short elem, String name, int id, int streamId, String param) {}
    public static void traceStream(String direction, boolean elem, String name, int id, int streamId, String param) {}

    public static <T, A, R> Collector<T, A, R> traceParam(
            String name,
            int id,
            int streamId,
            String paramAsString,
            Collector<T, A, R> base
    ) {
        return Collector.of(
                base.supplier(),
                (acc, t) -> {
                    int hashbefore = acc.hashCode();
                    base.accumulator().accept(acc, t);
                    System.out.println("before: " + hashbefore + " after: " + acc.hashCode());
                    if (hashbefore != acc.hashCode()) {
                        traceStream("END", acc, name, id, streamId, paramAsString, acc.toString());
                    } else {
                        traceStream("NOP", acc, name, id, streamId, paramAsString, acc.toString());
                    }
                },
                base.combiner(),
                base.finisher(),
                base.characteristics().toArray(new Collector.Characteristics[0])
        );
    }
    public static void collectAndTransformStreamOperationValues() {}
}