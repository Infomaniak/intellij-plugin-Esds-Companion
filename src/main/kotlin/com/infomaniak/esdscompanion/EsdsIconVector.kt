package com.infomaniak.esdscompanion

import com.intellij.ui.scale.JBUIScale
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.IntInsnNode
import org.jetbrains.org.objectweb.asm.tree.LdcInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Path2D
import javax.swing.Icon
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The drawing of a design system icon such as `EsdsIcons.Plus.Medium`, drawn for [widthDp] (24dp).
 *
 * It is read back from the icon's *compiled* getter, which is always on the project's classpath,
 * unlike sources. The generated `ImageVector.Builder { path { moveTo(5f, 12f) ... } }` code is fully
 * inlined, so the bytecode is a flat list of `PathBuilder` calls with constant arguments.
 */
internal class EsdsIconVector private constructor(
    val widthDp: Float,
    private val viewport: Float,
    private val paths: List<VectorPath>,
) {

    /** `24dp`, the size this icon is designed for. */
    val sizeLabel: String get() = (if (widthDp % 1 == 0f) widthDp.toInt().toString() else widthDp.toString()) + "dp"

    private class VectorPath(val shape: Shape, val filled: Boolean, val strokeWidth: Float?)

    /**
     * Paints the icon in a single [color], ignoring the icon's own one: the design system draws
     * them black, which would be unreadable on a dark theme.
     */
    inner class Preview(
        private val size: Int,
        private val color: () -> Color = { EsdsIconSettings.getInstance().color() },
    ) : Icon {
        val vector: EsdsIconVector get() = this@EsdsIconVector

        override fun getIconWidth(): Int = JBUIScale.scale(size)

        override fun getIconHeight(): Int = iconWidth

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
                g2.translate(x, y)
                val scale = iconWidth / viewport.toDouble()
                g2.scale(scale, scale)
                g2.color = color()
                for (path in paths) {
                    if (path.filled) g2.fill(path.shape)
                    val strokeWidth = path.strokeWidth ?: continue
                    // ponytail: always round caps and joins, as on 99% of the icons. Read them from
                    // the bytecode if a butt or mitered icon ever looks off at preview size.
                    g2.stroke = BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.draw(path.shape)
                }
            } finally {
                g2.dispose()
            }
        }
    }

    companion object {
        private const val VECTOR = "androidx/compose/ui/graphics/vector/"
        private const val BUILDER = "${VECTOR}ImageVector\$Builder"
        private const val PATH_BUILDER = "${VECTOR}PathBuilder"

        private val NEW_OBJECT = Any()

        /** Reads icon [name] (`Plus`) from its compiled `PlusKt` class, or `null` if that fails. */
        fun read(classBytes: ByteArray, name: String): EsdsIconVector? = runCatching {
            val node = ClassNode().also { ClassReader(classBytes).accept(it, ClassReader.SKIP_DEBUG) }
            val getter = node.methods.first { it.name == "get$name" }
            // The analyzer tracks what each stack slot and local holds, which constant arguments go
            // through (`24.dp` is stored in a local before reaching the builder).
            val frames = Analyzer(ConstantTracker).analyze(node.name, getter)

            var width: Float? = null
            var viewport: Float? = null
            val paths = mutableListOf<VectorPath>()
            var shape = ShapeBuilder()

            for ((index, insn) in getter.instructions.toArray().withIndex()) {
                val call = insn as? MethodInsnNode ?: continue
                val frame = frames[index] ?: continue
                val types = Type.getArgumentTypes(call.desc)
                val args = List(types.size) { (frame.getStack(frame.stackSize - types.size + it) as? Known)?.value }

                when {
                    // ImageVector.Builder(name, defaultWidth, defaultHeight, viewportWidth, ...)
                    call.owner == BUILDER && call.name == "<init>" -> {
                        width = args[1] as Float
                        viewport = args[3] as Float
                    }
                    call.owner == PATH_BUILDER && call.name == "<init>" -> shape = ShapeBuilder()
                    call.owner == PATH_BUILDER && call.name != "getNodes" ->
                        shape.add(call.name, args.map { (it as Number).toFloat() })
                    // addPath(nodes, pathFillType, name, fill, fillAlpha, stroke, strokeAlpha, strokeLineWidth, ...)
                    call.owner == BUILDER && call.name.startsWith("addPath") -> {
                        val nodes = types.indexOfFirst { it.descriptor == "Ljava/util/List;" }
                        paths += VectorPath(
                            shape = shape.build(evenOdd = args[nodes + 1] == "EvenOdd"),
                            filled = args[nodes + 3] === NEW_OBJECT,
                            strokeWidth = (args[nodes + 7] as Float).takeIf { args[nodes + 5] === NEW_OBJECT },
                        )
                    }
                }
            }
            EsdsIconVector(width!!, viewport!!, paths)
        }.getOrNull()
    }

    /** A stack or local value known to hold [value]: a number, a symbol (`EvenOdd`) or [NEW_OBJECT]. */
    private class Known(type: Type?, val value: Any) : BasicValue(type) {
        override fun equals(other: Any?) = other is Known && type == other.type && value == other.value
        override fun hashCode() = 31 * super.hashCode() + value.hashCode()
    }

    private object ConstantTracker : BasicInterpreter(Opcodes.ASM9) {

        override fun newOperation(insn: AbstractInsnNode): BasicValue = known(
            super.newOperation(insn),
            when (insn.opcode) {
                in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> insn.opcode - Opcodes.ICONST_0
                in Opcodes.FCONST_0..Opcodes.FCONST_2 -> (insn.opcode - Opcodes.FCONST_0).toFloat()
                Opcodes.BIPUSH, Opcodes.SIPUSH -> (insn as IntInsnNode).operand
                Opcodes.LDC -> (insn as LdcInsnNode).cst as? Number
                // A brush: `SolidColor(Color.Black)`, as opposed to `null` for "no fill"/"no stroke".
                Opcodes.NEW -> NEW_OBJECT
                else -> null
            },
        )!!

        override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
            val known = (value as? Known)?.value
            return known(
                super.unaryOperation(insn, value),
                when (insn.opcode) {
                    Opcodes.I2F -> (known as? Number)?.toFloat()
                    Opcodes.FNEG -> (known as? Float)?.unaryMinus()
                    Opcodes.CHECKCAST -> known
                    else -> null
                },
            )
        }

        override fun naryOperation(insn: AbstractInsnNode, values: List<BasicValue>): BasicValue? {
            val call = insn as? MethodInsnNode
            return known(
                super.naryOperation(insn, values),
                when {
                    call == null -> null
                    // `24.dp` goes through the `Dp(24f)` value class constructor.
                    call.name == "constructor-impl" -> (values.firstOrNull() as? Known)?.value
                    // `PathFillType.EvenOdd`, `StrokeCap.Round`... compile to `getEvenOdd-Rg-k1Os()`.
                    call.name.startsWith("get") && values.size <= 1 -> call.name.removePrefix("get").substringBefore('-')
                    else -> null
                },
            )
        }

        override fun merge(value1: BasicValue, value2: BasicValue): BasicValue =
            if (value1 == value2) value1 else super.merge(BasicValue(value1.type), BasicValue(value2.type))

        private fun known(value: BasicValue?, constant: Any?): BasicValue? =
            if (value == null || constant == null) value else Known(value.type, constant)
    }

    /** Replays Compose `PathBuilder` calls (`moveTo`, `arcToRelative`...) onto a Java2D path. */
    private class ShapeBuilder {
        private val path = Path2D.Float()
        private var x = 0f
        private var y = 0f
        private var startX = 0f
        private var startY = 0f
        private var controlX = 0f
        private var controlY = 0f
        private var lastWasCubic = false
        private var lastWasQuad = false

        fun build(evenOdd: Boolean): Shape =
            path.apply { windingRule = if (evenOdd) Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO }

        fun add(command: String, a: List<Float>) {
            val relative = command.endsWith("Relative")
            val ox = if (relative) x else 0f
            val oy = if (relative) y else 0f
            val wasCubic = lastWasCubic
            val wasQuad = lastWasQuad
            lastWasCubic = false
            lastWasQuad = false

            when (command.removeSuffix("Relative")) {
                "close" -> {
                    path.closePath()
                    x = startX
                    y = startY
                }
                "moveTo" -> {
                    x = ox + a[0]
                    y = oy + a[1]
                    startX = x
                    startY = y
                    path.moveTo(x, y)
                }
                "lineTo" -> lineTo(ox + a[0], oy + a[1])
                "horizontalLineTo" -> lineTo(ox + a[0], y)
                "verticalLineTo" -> lineTo(x, oy + a[0])
                "curveTo" -> cubicTo(ox + a[0], oy + a[1], ox + a[2], oy + a[3], ox + a[4], oy + a[5])
                "reflectiveCurveTo" -> cubicTo(
                    if (wasCubic) 2 * x - controlX else x, if (wasCubic) 2 * y - controlY else y,
                    ox + a[0], oy + a[1], ox + a[2], oy + a[3],
                )
                "quadTo" -> quadTo(ox + a[0], oy + a[1], ox + a[2], oy + a[3])
                "reflectiveQuadTo" -> quadTo(
                    if (wasQuad) 2 * x - controlX else x, if (wasQuad) 2 * y - controlY else y,
                    ox + a[0], oy + a[1],
                )
                "arcTo" -> {
                    arcTo(a[0].toDouble(), a[1].toDouble(), a[2].toDouble(), a[3] != 0f, a[4] != 0f, ox + a[5], oy + a[6])
                    x = ox + a[5]
                    y = oy + a[6]
                }
                else -> error("Unsupported PathBuilder.$command")
            }
        }

        private fun lineTo(toX: Float, toY: Float) {
            path.lineTo(toX, toY)
            x = toX
            y = toY
        }

        private fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            path.curveTo(x1, y1, x2, y2, x3, y3)
            controlX = x2
            controlY = y2
            x = x3
            y = y3
            lastWasCubic = true
        }

        private fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
            path.quadTo(x1, y1, x2, y2)
            controlX = x1
            controlY = y1
            x = x2
            y = y2
            lastWasQuad = true
        }

        /** SVG elliptical arc, converted to its center parameterization (SVG spec, appendix F.6.5). */
        private fun arcTo(rxIn: Double, ryIn: Double, degrees: Double, large: Boolean, sweep: Boolean, toX: Float, toY: Float) {
            val x0 = x.toDouble()
            val y0 = y.toDouble()
            if (x0 == toX.toDouble() && y0 == toY.toDouble()) return
            var rx = abs(rxIn)
            var ry = abs(ryIn)
            if (rx == 0.0 || ry == 0.0) return path.lineTo(toX, toY)

            val angle = Math.toRadians(degrees % 360)
            val cos = cos(angle)
            val sin = sin(angle)
            val dx = (x0 - toX) / 2
            val dy = (y0 - toY) / 2
            val x1 = cos * dx + sin * dy
            val y1 = -sin * dx + cos * dy

            val radiiCheck = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
            if (radiiCheck > 1) {
                rx *= sqrt(radiiCheck)
                ry *= sqrt(radiiCheck)
            }
            val numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
            val coefficient = (if (large == sweep) -1 else 1) *
                sqrt((numerator / (rx * rx * y1 * y1 + ry * ry * x1 * x1)).coerceAtLeast(0.0))
            val cx1 = coefficient * rx * y1 / ry
            val cy1 = -coefficient * ry * x1 / rx
            val cx = (x0 + toX) / 2 + cos * cx1 - sin * cy1
            val cy = (y0 + toY) / 2 + sin * cx1 + cos * cy1

            val ux = (x1 - cx1) / rx
            val uy = (y1 - cy1) / ry
            val vx = (-x1 - cx1) / rx
            val vy = (-y1 - cy1) / ry
            val start = Math.toDegrees((if (uy < 0) -1 else 1) * acos((ux / sqrt(ux * ux + uy * uy)).coerceIn(-1.0, 1.0)))
            var extent = Math.toDegrees(
                (if (ux * vy - uy * vx < 0) -1 else 1) *
                    acos(((ux * vx + uy * vy) / sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))).coerceIn(-1.0, 1.0)),
            )
            if (!sweep && extent > 0) extent -= 360 else if (sweep && extent < 0) extent += 360

            // Arc2D angles run counter-clockwise on screen, hence the negations.
            val arc = Arc2D.Double(cx - rx, cy - ry, rx * 2, ry * 2, -start, -extent, Arc2D.OPEN)
            path.append(AffineTransform.getRotateInstance(angle, cx, cy).createTransformedShape(arc), true)
        }
    }
}
