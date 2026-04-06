package nextflow.nfr.codec

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.channels.Channels
import java.nio.file.Files
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.List
import java.util.Map
import nextflow.nfr.value.ValueGraphCodec
import nextflow.nfr.value.ValueGraphTag
import nextflow.nfr.value.ValueNode
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.BigIntVector
import org.apache.arrow.vector.BitVector
import org.apache.arrow.vector.Float8Vector
import org.apache.arrow.vector.FieldVector
import org.apache.arrow.vector.IntVector
import org.apache.arrow.vector.VarCharVector
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.ipc.ArrowStreamReader
import org.apache.arrow.vector.ipc.ArrowStreamWriter
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.Field
import org.apache.arrow.vector.types.pojo.FieldType
import org.apache.arrow.vector.types.pojo.Schema

@CompileStatic
class ArrowJavaCodec implements IpcCodec {

    static final String CODEC_NAME = 'arrow-java'
    static final String SECTION_CONTROL = '__nfr_control__'
    static final String SECTION_DATA = '__nfr_data__'

    private static final Schema IPC_SCHEMA = new Schema([
        nullableUtf8('section'),
        nullableInt32('protocol_version'),
        nullableUtf8('call_id'),
        nullableUtf8('function'),
        nullableUtf8('script_mode'),
        nullableUtf8('script_ref'),
        nullableUtf8('payload_kind'),
        nullableUtf8('status'),
        nullableUtf8('result_kind'),
        nullableUtf8('error_class'),
        nullableUtf8('error_message'),
        nullableInt64('value_id'),
        nullableInt64('parent_id'),
        nullableUtf8('key'),
        nullableInt32('index'),
        nullableUtf8('tag'),
        nullableUtf8('v_string'),
        nullableInt64('v_int64'),
        nullableFloat64('v_float64'),
        nullableBool('v_bool')
    ])

    @Override
    String getName() {
        return CODEC_NAME
    }

    @Override
    void writeRequest(Path ipcPath, Map<String,Object> control, Object data) {
        List<ValueNode> nodes = ValueGraphCodec.encode(data)
        int rowCount = 1 + nodes.size()

        try (RootAllocator allocator = new RootAllocator();
             VectorSchemaRoot root = VectorSchemaRoot.create(IPC_SCHEMA, allocator);
             def out = Files.newOutputStream(ipcPath);
             def channel = Channels.newChannel(out);
             ArrowStreamWriter writer = new ArrowStreamWriter(root, null, channel)) {

            root.allocateNew()
            setControlRow(root, 0, control)

            int i = 1
            for (ValueNode node : nodes) {
                setDataRow(root, i++, node)
            }

            root.setRowCount(rowCount)
            writer.start()
            writer.writeBatch()
            writer.end()
        }
    }

    @Override
    DecodedResponse readResponse(Path ipcPath) {
        if (!Files.exists(ipcPath)) {
            throw new CodecException("IPC response file not found: ${ipcPath}")
        }

        try (RootAllocator allocator = new RootAllocator();
             def in = Files.newInputStream(ipcPath);
             def channel = Channels.newChannel(in);
             ArrowStreamReader reader = new ArrowStreamReader(channel, allocator)) {
            Map<String,Object> control = new LinkedHashMap<>()
            List<ValueNode> nodes = new ArrayList<>()

            boolean hasAnyBatch = false
            while (reader.loadNextBatch()) {
                hasAnyBatch = true
                VectorSchemaRoot root = reader.getVectorSchemaRoot()
                for (int i = 0; i < root.getRowCount(); i++) {
                    String section = readUtf8(root, 'section', i)
                    if (SECTION_CONTROL == section) {
                        control = readControl(root, i)
                    } else if (SECTION_DATA == section) {
                        nodes.add(readValueNode(root, i))
                    }
                }
            }

            if (!hasAnyBatch) {
                throw new CodecException('IPC stream has no batches')
            }
            if (control.isEmpty()) {
                throw new CodecException('IPC stream missing control section')
            }

            Object decoded = nodes.isEmpty() ? null : ValueGraphCodec.decode(nodes)
            return new DecodedResponse(control, decoded)
        }
    }

    private static void setControlRow(VectorSchemaRoot root, int row, Map<String,Object> control) {
        writeUtf8(root, 'section', row, SECTION_CONTROL)
        writeInt32(root, 'protocol_version', row, asInteger(control.get('protocol_version')))
        writeUtf8(root, 'call_id', row, asString(control.get('call_id')))
        writeUtf8(root, 'function', row, asString(control.get('function')))
        writeUtf8(root, 'script_mode', row, asString(control.get('script_mode')))
        writeUtf8(root, 'script_ref', row, asString(control.get('script_ref')))
        writeUtf8(root, 'payload_kind', row, asString(control.get('payload_kind')))
        writeUtf8(root, 'status', row, asString(control.get('status')))
        writeUtf8(root, 'result_kind', row, asString(control.get('result_kind')))
        writeUtf8(root, 'error_class', row, asString(control.get('error_class')))
        writeUtf8(root, 'error_message', row, asString(control.get('error_message')))

        writeInt64(root, 'value_id', row, null)
        writeInt64(root, 'parent_id', row, null)
        writeUtf8(root, 'key', row, null)
        writeInt32(root, 'index', row, null)
        writeUtf8(root, 'tag', row, null)
        writeUtf8(root, 'v_string', row, null)
        writeInt64(root, 'v_int64', row, null)
        writeFloat64(root, 'v_float64', row, null)
        writeBool(root, 'v_bool', row, null)
    }

    private static void setDataRow(VectorSchemaRoot root, int row, ValueNode node) {
        writeUtf8(root, 'section', row, SECTION_DATA)

        writeInt32(root, 'protocol_version', row, null)
        writeUtf8(root, 'call_id', row, null)
        writeUtf8(root, 'function', row, null)
        writeUtf8(root, 'script_mode', row, null)
        writeUtf8(root, 'script_ref', row, null)
        writeUtf8(root, 'payload_kind', row, null)
        writeUtf8(root, 'status', row, null)
        writeUtf8(root, 'result_kind', row, null)
        writeUtf8(root, 'error_class', row, null)
        writeUtf8(root, 'error_message', row, null)

        writeInt64(root, 'value_id', row, node.valueId)
        writeInt64(root, 'parent_id', row, node.parentId)
        writeUtf8(root, 'key', row, node.key)
        writeInt32(root, 'index', row, node.index)
        writeUtf8(root, 'tag', row, node.tag.wireName)
        writeUtf8(root, 'v_string', row, node.vString)
        writeInt64(root, 'v_int64', row, node.vInt64)
        writeFloat64(root, 'v_float64', row, node.vFloat64)
        writeBool(root, 'v_bool', row, node.vBool)
    }

    private static ValueNode readValueNode(VectorSchemaRoot root, int row) {
        return new ValueNode(
            readInt64(root, 'value_id', row) ?: 0L,
            readInt64(root, 'parent_id', row),
            readUtf8(root, 'key', row),
            readInt32(root, 'index', row),
            ValueGraphTag.fromWireName(readUtf8(root, 'tag', row)),
            readUtf8(root, 'v_string', row),
            readInt64(root, 'v_int64', row),
            readFloat64(root, 'v_float64', row),
            readBool(root, 'v_bool', row)
        )
    }

    private static Map<String,Object> readControl(VectorSchemaRoot root, int row) {
        Map<String,Object> out = new LinkedHashMap<>()
        putIfNotNull(out, 'protocol_version', readInt32(root, 'protocol_version', row))
        putIfNotNull(out, 'call_id', readUtf8(root, 'call_id', row))
        putIfNotNull(out, 'function', readUtf8(root, 'function', row))
        putIfNotNull(out, 'script_mode', readUtf8(root, 'script_mode', row))
        putIfNotNull(out, 'script_ref', readUtf8(root, 'script_ref', row))
        putIfNotNull(out, 'payload_kind', readUtf8(root, 'payload_kind', row))
        putIfNotNull(out, 'status', readUtf8(root, 'status', row))
        putIfNotNull(out, 'result_kind', readUtf8(root, 'result_kind', row))
        putIfNotNull(out, 'error_class', readUtf8(root, 'error_class', row))
        putIfNotNull(out, 'error_message', readUtf8(root, 'error_message', row))
        return out
    }

    private static void putIfNotNull(Map<String,Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value)
        }
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null
        }
        return ((Number)value).intValue()
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value)
    }

    private static void writeUtf8(VectorSchemaRoot root, String name, int row, String value) {
        VarCharVector vector = (VarCharVector)root.getVector(name)
        if (value == null) {
            vector.setNull(row)
        } else {
            vector.setSafe(row, value.getBytes(StandardCharsets.UTF_8))
        }
    }

    private static void writeInt32(VectorSchemaRoot root, String name, int row, Integer value) {
        IntVector vector = (IntVector)root.getVector(name)
        if (value == null) {
            vector.setNull(row)
        } else {
            vector.setSafe(row, value)
        }
    }

    private static void writeInt64(VectorSchemaRoot root, String name, int row, Long value) {
        BigIntVector vector = (BigIntVector)root.getVector(name)
        if (value == null) {
            vector.setNull(row)
        } else {
            vector.setSafe(row, value)
        }
    }

    private static void writeFloat64(VectorSchemaRoot root, String name, int row, Double value) {
        Float8Vector vector = (Float8Vector)root.getVector(name)
        if (value == null) {
            vector.setNull(row)
        } else {
            vector.setSafe(row, value)
        }
    }

    private static void writeBool(VectorSchemaRoot root, String name, int row, Boolean value) {
        BitVector vector = (BitVector)root.getVector(name)
        if (value == null) {
            vector.setNull(row)
        } else {
            vector.setSafe(row, value ? 1 : 0)
        }
    }

    private static String readUtf8(VectorSchemaRoot root, String name, int row) {
        FieldVector vector = root.getVector(name)
        if (vector.isNull(row)) {
            return null
        }
        if (vector instanceof VarCharVector) {
            byte[] value = ((VarCharVector)vector).get(row)
            return new String(value, StandardCharsets.UTF_8)
        }
        Object obj = vector.getObject(row)
        return obj == null ? null : String.valueOf(obj)
    }

    private static Integer readInt32(VectorSchemaRoot root, String name, int row) {
        FieldVector vector = root.getVector(name)
        if (vector.isNull(row)) {
            return null
        }
        if (vector instanceof IntVector) {
            return ((IntVector)vector).get(row)
        }
        if (vector instanceof BigIntVector) {
            return (int)((BigIntVector)vector).get(row)
        }
        if (vector instanceof Float8Vector) {
            return (int)((Float8Vector)vector).get(row)
        }
        Object obj = vector.getObject(row)
        return obj == null ? null : ((Number)obj).intValue()
    }

    private static Long readInt64(VectorSchemaRoot root, String name, int row) {
        FieldVector vector = root.getVector(name)
        if (vector.isNull(row)) {
            return null
        }
        if (vector instanceof BigIntVector) {
            return ((BigIntVector)vector).get(row)
        }
        if (vector instanceof IntVector) {
            return (long)((IntVector)vector).get(row)
        }
        if (vector instanceof Float8Vector) {
            return (long)((Float8Vector)vector).get(row)
        }
        Object obj = vector.getObject(row)
        return obj == null ? null : ((Number)obj).longValue()
    }

    private static Double readFloat64(VectorSchemaRoot root, String name, int row) {
        Float8Vector vector = (Float8Vector)root.getVector(name)
        return vector.isNull(row) ? null : vector.get(row)
    }

    private static Boolean readBool(VectorSchemaRoot root, String name, int row) {
        FieldVector vector = root.getVector(name)
        if (vector.isNull(row)) {
            return null
        }
        if (vector instanceof BitVector) {
            return ((BitVector)vector).get(row) != 0
        }
        if (vector instanceof IntVector) {
            return ((IntVector)vector).get(row) != 0
        }
        if (vector instanceof Float8Vector) {
            return ((Float8Vector)vector).get(row) != 0d
        }
        Object obj = vector.getObject(row)
        if (obj instanceof Boolean) {
            return (Boolean)obj
        }
        return ((Number)obj).intValue() != 0
    }

    private static Field nullableUtf8(String name) {
        return new Field(name, FieldType.nullable(new ArrowType.Utf8()), null)
    }

    private static Field nullableInt32(String name) {
        return new Field(name, FieldType.nullable(new ArrowType.Int(32, true)), null)
    }

    private static Field nullableInt64(String name) {
        return new Field(name, FieldType.nullable(new ArrowType.Int(64, true)), null)
    }

    private static Field nullableFloat64(String name) {
        return new Field(name, FieldType.nullable(new ArrowType.FloatingPoint(org.apache.arrow.vector.types.FloatingPointPrecision.DOUBLE)), null)
    }

    private static Field nullableBool(String name) {
        return new Field(name, FieldType.nullable(new ArrowType.Bool()), null)
    }
}
