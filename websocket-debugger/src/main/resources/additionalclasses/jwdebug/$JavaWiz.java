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
                    System.out.println("ACC: " + acc);
//                    boolean isMap = acc instanceof java.util.Map;
                    String accStr = "";
//                    if (isMap) {
//                        String finalAccString = acc.toString(); // Fallback
//
//                            Map<?, ?> mapAcc = (Map<?, ?>) acc;
//                            StringBuilder sb = new StringBuilder("{");
//
//                            Iterator<? extends Map.Entry<?, ?>> mapIterator = mapAcc.entrySet().iterator();
//                            while (mapIterator.hasNext()) {
//                                Map.Entry<?, ?> entry = mapIterator.next();
//                                sb.append(entry.getKey()).append("=");
//
//                                Object value = entry.getValue();
//
//                                // Prüfen, ob der Wert eine Collection (z.B. eine Liste) ist
//                                if (value instanceof Collection) {
//                                    Collection<?> valueCollection = (Collection<?>) value;
//
//                                    // Erzeuge einen String aus den identityHashCodes der Elemente in der Collection
//                                    String idListString = valueCollection.stream()
//                                            .map(item -> String.valueOf(System.identityHashCode(item)))
//                                            .collect(Collectors.joining(", ", "[", "]"));
//
//                                    sb.append(idListString);
//                                } else {
//                                    // Fallback, falls der Wert keine Collection ist
//                                    sb.append(System.identityHashCode(value));
//                                }
//
//                                if (mapIterator.hasNext()) {
//                                    sb.append(", ");
//                                }
//                            }
//                            sb.append("}");
//                            finalAccString = sb.toString();
//
//                        System.out.println("MAP MIT ARRAY-IDs: " + finalAccString);
//                    }
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