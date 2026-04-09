package nextflow.nfr.value

import groovy.transform.CompileStatic
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.List
import java.util.Map
import java.util.Set

@CompileStatic
class ValueGraphValidator {

    static void validate(List<ValueNode> nodes) {
        if (!nodes || nodes.isEmpty()) {
            throw new IllegalArgumentException('Value graph cannot be empty')
        }

        Map<Long, ValueNode> byId = new HashMap<>()
        Map<Long, List<ValueNode>> children = new HashMap<>()
        int roots = 0

        for (ValueNode node : nodes) {
            if (byId.containsKey(node.valueId)) {
                throw new IllegalArgumentException("Duplicate value_id in value graph: ${node.valueId}")
            }
            byId.put(node.valueId, node)
            if (node.parentId == null) {
                roots++
            } else {
                children.computeIfAbsent(node.parentId, k -> new ArrayList<>()).add(node)
            }
            validateValueColumns(node)
        }

        if (roots != 1) {
            throw new IllegalArgumentException("Value graph must contain exactly one root, found: ${roots}")
        }

        for (ValueNode node : nodes) {
            if (node.parentId != null && !byId.containsKey(node.parentId)) {
                throw new IllegalArgumentException("Value graph has missing parent for node ${node.valueId}: ${node.parentId}")
            }
        }

        for (ValueNode parent : nodes) {
            List<ValueNode> kids = children.get(parent.valueId)
            if (kids == null || kids.isEmpty()) {
                continue
            }
            validateParentChildRules(parent, kids)
        }
    }

    private static void validateParentChildRules(ValueNode parent, List<ValueNode> kids) {
        switch (parent.tag) {
            case ValueGraphTag.LIST:
                Set<Integer> seenIndex = new HashSet<>()
                for (ValueNode child : kids) {
                    if (child.index == null) {
                        throw new IllegalArgumentException("List child missing index for parent ${parent.valueId}")
                    }
                    if (child.index < 0) {
                        throw new IllegalArgumentException("List child has negative index ${child.index} for parent ${parent.valueId}")
                    }
                    if (child.key != null) {
                        throw new IllegalArgumentException("List child must not set key for parent ${parent.valueId}")
                    }
                    if (!seenIndex.add(child.index)) {
                        throw new IllegalArgumentException("Duplicate list child index ${child.index} for parent ${parent.valueId}")
                    }
                }
                break

            case ValueGraphTag.MAP:
            case ValueGraphTag.DATA_FRAME:
                Set<String> seenKey = new HashSet<>()
                for (ValueNode child : kids) {
                    if (child.key == null || child.key.isEmpty()) {
                        throw new IllegalArgumentException("Map/data_frame child missing key for parent ${parent.valueId}")
                    }
                    if (child.index != null) {
                        throw new IllegalArgumentException("Map/data_frame child must not set index for parent ${parent.valueId}")
                    }
                    if (!seenKey.add(child.key)) {
                        throw new IllegalArgumentException("Duplicate map/data_frame child key '${child.key}' for parent ${parent.valueId}")
                    }
                }
                break

            default:
                throw new IllegalArgumentException("Scalar/null/NA node cannot have children: parent ${parent.valueId} (${parent.tag.wireName})")
        }
    }

    private static void validateValueColumns(ValueNode node) {
        int nonNull = 0
        if (node.vString != null) nonNull++
        if (node.vInt64 != null) nonNull++
        if (node.vFloat64 != null) nonNull++
        if (node.vBool != null) nonNull++

        switch (node.tag) {
            case ValueGraphTag.STRING:
                if (node.vString == null || nonNull != 1) {
                    throw new IllegalArgumentException("Invalid value columns for string node ${node.valueId}")
                }
                break
            case ValueGraphTag.INT64:
                if (node.vInt64 == null || nonNull != 1) {
                    throw new IllegalArgumentException("Invalid value columns for int64 node ${node.valueId}")
                }
                break
            case ValueGraphTag.FLOAT64:
                if (node.vFloat64 == null || nonNull != 1) {
                    throw new IllegalArgumentException("Invalid value columns for float64 node ${node.valueId}")
                }
                break
            case ValueGraphTag.BOOL:
                if (node.vBool == null || nonNull != 1) {
                    throw new IllegalArgumentException("Invalid value columns for bool node ${node.valueId}")
                }
                break
            default:
                if (nonNull != 0) {
                    throw new IllegalArgumentException("Node ${node.valueId} (${node.tag.wireName}) must not set scalar value columns")
                }
        }
    }
}
