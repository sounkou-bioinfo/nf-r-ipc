package nextflow.nfr.value

import groovy.transform.CompileStatic
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.List
import java.util.Map

@CompileStatic
class ValueGraphCodec {

    static List<ValueNode> encode(Object rootValue) {
        List<ValueNode> nodes = new ArrayList<>()
        long[] nextId = [1L] as long[]
        appendNode(nodes, nextId, null, null, null, rootValue)
        return nodes
    }

    static Object decode(List<ValueNode> nodes) {
        if (!nodes || nodes.isEmpty()) {
            throw new IllegalArgumentException('Value graph cannot be empty')
        }

        Map<Long, ValueNode> byId = new HashMap<>()
        Map<Long, List<ValueNode>> children = new HashMap<>()
        ValueNode root = null

        for (ValueNode node : nodes) {
            byId.put(node.valueId, node)
            if (node.parentId == null) {
                if (root != null) {
                    throw new IllegalArgumentException('Value graph has more than one root')
                }
                root = node
            } else {
                children.computeIfAbsent(node.parentId, k -> new ArrayList<>()).add(node)
            }
        }

        if (root == null) {
            throw new IllegalArgumentException('Value graph is missing root node')
        }

        return decodeNode(root, children)
    }

    private static long appendNode(
        List<ValueNode> nodes,
        long[] nextId,
        Long parentId,
        String key,
        Integer index,
        Object value
    ) {
        long id = nextId[0]++

        if (value == null) {
            nodes.add(new ValueNode(id, parentId, key, index, ValueGraphTag.NULL, null, null, null, null))
            return id
        }

        if (value instanceof NAValue) {
            ValueGraphTag naTag
            switch ((NAValue)value) {
                case NAValue.LOGICAL:
                    naTag = ValueGraphTag.NA_LOGICAL
                    break
                case NAValue.INTEGER:
                    naTag = ValueGraphTag.NA_INTEGER
                    break
                case NAValue.DOUBLE:
                    naTag = ValueGraphTag.NA_DOUBLE
                    break
                case NAValue.CHARACTER:
                    naTag = ValueGraphTag.NA_CHARACTER
                    break
                default:
                    throw new IllegalArgumentException("Unsupported NA value variant: ${value}")
            }
            nodes.add(new ValueNode(id, parentId, key, index, naTag, null, null, null, null))
            return id
        }

        if (value instanceof Map) {
            nodes.add(new ValueNode(id, parentId, key, index, ValueGraphTag.MAP, null, null, null, null))
            Map mapValue = (Map)value
            mapValue.each { k, v ->
                appendNode(nodes, nextId, id, String.valueOf(k), null, v)
            }
            return id
        }

        if (value instanceof List) {
            nodes.add(new ValueNode(id, parentId, key, index, ValueGraphTag.LIST, null, null, null, null))
            List listValue = (List)value
            for (int i = 0; i < listValue.size(); i++) {
                appendNode(nodes, nextId, id, null, i, listValue.get(i))
            }
            return id
        }

        if (value instanceof CharSequence) {
            nodes.add(new ValueNode(id, parentId, key, index, ValueGraphTag.STRING, value.toString(), null, null, null))
            return id
        }

        if (value instanceof Boolean) {
            nodes.add(new ValueNode(id, parentId, key, index, ValueGraphTag.BOOL, null, null, null, (Boolean)value))
            return id
        }

        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            nodes.add(new ValueNode(id, parentId, key, index, ValueGraphTag.INT64, null, ((Number)value).longValue(), null, null))
            return id
        }

        if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
            nodes.add(new ValueNode(id, parentId, key, index, ValueGraphTag.FLOAT64, null, null, ((Number)value).doubleValue(), null))
            return id
        }

        throw new IllegalArgumentException("Unsupported value type in value graph: ${value.getClass().name}")
    }

    private static Object decodeNode(ValueNode node, Map<Long, List<ValueNode>> children) {
        switch (node.tag) {
            case ValueGraphTag.NULL:
                return null
            case ValueGraphTag.NA_LOGICAL:
                return NAValue.LOGICAL
            case ValueGraphTag.NA_INTEGER:
                return NAValue.INTEGER
            case ValueGraphTag.NA_DOUBLE:
                return NAValue.DOUBLE
            case ValueGraphTag.NA_CHARACTER:
                return NAValue.CHARACTER
            case ValueGraphTag.STRING:
                return node.vString
            case ValueGraphTag.BOOL:
                return node.vBool
            case ValueGraphTag.INT64:
                return node.vInt64
            case ValueGraphTag.FLOAT64:
                return node.vFloat64
            case ValueGraphTag.MAP:
                Map<String,Object> result = new LinkedHashMap<>()
                List<ValueNode> mapChildren = children.get(node.valueId) ?: Collections.<ValueNode>emptyList()
                for (ValueNode child : mapChildren) {
                    if (child.key == null) {
                        throw new IllegalArgumentException("Map child missing key for parent ${node.valueId}")
                    }
                    result.put(child.key, decodeNode(child, children))
                }
                return result
            case ValueGraphTag.LIST:
                List<ValueNode> listChildren = children.get(node.valueId) ?: Collections.<ValueNode>emptyList()
                List<ValueNode> sorted = new ArrayList<>(listChildren)
                Collections.sort(sorted, (a, b) -> {
                    Integer ai = a.index == null ? Integer.MAX_VALUE : a.index
                    Integer bi = b.index == null ? Integer.MAX_VALUE : b.index
                    return ai <=> bi
                } as Comparator<ValueNode>)
                List<Object> out = new ArrayList<>(sorted.size())
                for (ValueNode child : sorted) {
                    out.add(decodeNode(child, children))
                }
                return out
            default:
                throw new IllegalArgumentException("Unsupported value node tag: ${node.tag}")
        }
    }
}
