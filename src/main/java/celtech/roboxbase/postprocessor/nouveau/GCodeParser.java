/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package celtech.roboxbase.postprocessor.nouveau;

import celtech.roboxbase.postprocessor.nouveau.nodes.CommentNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.ExtrusionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.FillSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.GCodeDirectiveNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.GCodeEventNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.InnerPerimeterSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.LayerChangeDirectiveNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.LayerNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.MCodeNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.ObjectDelineationNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.OrphanSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.OuterPerimeterSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.PreambleNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.RetractNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.SkinSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.SkirtSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.SupportInterfaceSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.SupportSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.TravelNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.UnrecognisedLineNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.UnretractNode;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;
import org.parboiled.Action;
import org.parboiled.BaseParser;
import org.parboiled.Context;
import org.parboiled.Rule;
import org.parboiled.annotations.SuppressSubnodes;
import org.parboiled.support.Var;

/**
 *
 * @author admin
 */
public abstract class GCodeParser extends BaseParser<GCodeEventNode> {
    
    final static Stenographer STENO = StenographerFactory.getStenographer(CuraGCodeParser.class.getName());
    LayerNode thisLayer = new LayerNode();
    float feedrateInForce = -1;
    int currentLineNumber = 0;
    double currentLayerHeight = 0;
    Var<Integer> currentObject = new Var<>(-1);
    double printVolumeWidth = 0;
    double printVolumeDepth = 0;
    double printVolumeHeight = 0;
    //This constant was introduced to allow the slicer to generate output that marginally exceeds 100mm
    //Firmware v753 onwards supports a 100.2mm max Z
    //See ARR-26 and ARR-21
    final double printVolumeHeightTolerance = 0.2;
    
    public int getCurrentLineNumber() {
        return currentLineNumber;
    }

    public void setStartingLineNumber(int startingLineNumber) {
        this.currentLineNumber = startingLineNumber;
    }

    public void setFeedrateInForce(float feedrate) {
        this.feedrateInForce = feedrate;
    }

    public float getFeedrateInForce() {
        return feedrateInForce;
    }

    public LayerNode getLayerNode() {
        return thisLayer;
    }

    public void resetLayer() {
        thisLayer = new LayerNode();
    }
    
    void validateXPosition(double value) {
        if (value > printVolumeWidth
                || value < 0) {
            throw new ParserInputException("X value outside bed: " + value);
        }
    }

    //Inbound Y translates to Z
    void validateYPosition(double value) {
        if (value > printVolumeDepth
                || value < 0) {
            throw new ParserInputException("Y value outside bed: " + value);
        }
    }

    //Inbound Z translates to -Y
    void validateZPosition(double value) {
        if (value > (printVolumeHeight + printVolumeHeightTolerance)
                || value < 0) {
            throw new ParserInputException("Z value outside bed: " + value);
        }
    }
    
    public void setPrintVolumeBounds(double printVolumeWidth,
        double printVolumeDepth,
        double printVolumeHeight) {
        this.printVolumeWidth = printVolumeWidth;
        this.printVolumeDepth = printVolumeDepth;
        this.printVolumeHeight = printVolumeHeight;
    }
    
    public abstract Rule Layer();
    
    Rule Preamble()
    {
        return Sequence(
                OneOrMore(CommentDirective()),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        PreambleNode node = new PreambleNode();
                        while (context.getValueStack().iterator().hasNext())
                        {
                            node.getChildren().add(0, (GCodeEventNode) context.getValueStack().pop());
                        }
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    // T1 or T12 or T123...
    Rule ObjectSection()
    {
        ObjectSectionActionClass objectSectionAction = new ObjectSectionActionClass();
        Var<Integer> objectNumber = new Var<>(0);

        return Sequence(
                Sequence('T', OneOrMore(Digit()),
                        objectNumber.set(Integer.valueOf(match())),
                        currentObject.set(Integer.valueOf(match())),
                        Newline()
                ),
                objectSectionAction,
                Optional(
                        Sequence(TravelDirective(),
                                new Action()
                                {
                                    @Override
                                    public boolean run(Context context)
                                    {
                                        objectSectionAction.getNode().addChildAtEnd((GCodeEventNode) context.getValueStack().pop());
                                        return true;
                                    }
                                }
                        )
                ),
                OneOrMore(
                        Sequence(
                                AnySection(),
                                new Action()
                                {
                                    @Override
                                    public boolean run(Context context)
                                    {
                                        objectSectionAction.getNode().addChildAtEnd((GCodeEventNode) context.getValueStack().pop());
                                        return true;
                                    }
                                }
                        )
                ),
                new Action()
                {
                    @Override
                    public boolean run(Context context
                    )
                    {
                        ObjectDelineationNode node = objectSectionAction.getNode();
                        node.setObjectNumber(objectNumber.get());
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    abstract Rule OrphanObjectSection();

    //Orphan section
    //No type
    Rule OrphanSection()
    {
        return Sequence(
                NotASection(),
                OneOrMore(ChildDirective()),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        OrphanSectionNode node = new OrphanSectionNode();
                        while (context.getValueStack().iterator().hasNext())
                        {
                            node.addChildAtStart((GCodeEventNode) context.getValueStack().pop());
                        }
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    //Cura fill Section
    //;TYPE:FILL
    Rule FillSection()
    {
        return Sequence(
                FillSectionNode.designator,
                Newline(),
                OneOrMore(ChildDirective()),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        FillSectionNode node = new FillSectionNode();
                        while (context.getValueStack().iterator().hasNext())
                        {
                            node.addChildAtStart((GCodeEventNode) context.getValueStack().pop());
                        }
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    //Cura skin Section
    //;TYPE:SKIN
    Rule SkinSection()
    {
        return Sequence(
                SkinSectionNode.designator,
                Newline(),
                OneOrMore(ChildDirective()),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        SkinSectionNode node = new SkinSectionNode();
                        while (context.getValueStack().iterator().hasNext())
                        {
                            node.addChildAtStart((GCodeEventNode) context.getValueStack().pop());
                        }
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    //Cura support Section
    Rule SupportSection()
    {
        return Sequence(
                SupportSectionNode.designator,
                Newline(),
                OneOrMore(ChildDirective()),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        SupportSectionNode node = new SupportSectionNode();
                        while (context.getValueStack().iterator().hasNext())
                        {
                            node.addChildAtStart((GCodeEventNode) context.getValueStack().pop());
                        }
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    //Cura skirt Section
    Rule SkirtSection()
    {
        return Sequence(
                SkirtSectionNode.designator,
                Newline(),
                OneOrMore(ChildDirective()),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        SkirtSectionNode node = new SkirtSectionNode();
                        while (context.getValueStack().iterator().hasNext())
                        {
                            node.addChildAtStart((GCodeEventNode) context.getValueStack().pop());
                        }
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    //Cura support interface Section
    Rule SupportInterfaceSection()
    {
        return Sequence(
                SupportInterfaceSectionNode.designator,
                Newline(),
                OneOrMore(ChildDirective()),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        SupportInterfaceSectionNode node = new SupportInterfaceSectionNode();
                        while (context.getValueStack().iterator().hasNext())
                        {
                            node.addChildAtStart((GCodeEventNode) context.getValueStack().pop());
                        }
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

//Cura outer perimeter section
    //;TYPE:WALL-OUTER
    Rule OuterPerimeterSection()
    {
        return Sequence(
                OuterPerimeterSectionNode.designator,
                Newline(),
                OneOrMore(ChildDirective()),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        OuterPerimeterSectionNode node = new OuterPerimeterSectionNode();
                        while (context.getValueStack().iterator().hasNext())
                        {
                            node.addChildAtStart((GCodeEventNode) context.getValueStack().pop());
                        }
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    //Cura inner perimeter section
    //;TYPE:WALL-INNER
    Rule InnerPerimeterSection()
    {
        return Sequence(
                InnerPerimeterSectionNode.designator,
                Newline(),
                OneOrMore(ChildDirective()),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        InnerPerimeterSectionNode node = new InnerPerimeterSectionNode();
                        node.setGCodeLineNumber(++currentLineNumber);
                        while (context.getValueStack().iterator().hasNext())
                        {
                            node.addChildAtStart((GCodeEventNode) context.getValueStack().pop());
                        }
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    Rule NotASection()
    {
        return Sequence(
                TestNot(FillSectionNode.designator),
                TestNot(InnerPerimeterSectionNode.designator),
                TestNot(OuterPerimeterSectionNode.designator),
                TestNot(SkinSectionNode.designator),
                TestNot(SupportInterfaceSectionNode.designator),
                TestNot(SupportSectionNode.designator));
    }

    Rule AnySection()
    {
        return FirstOf(
                FillSection(),
                InnerPerimeterSection(),
                OuterPerimeterSection(),
                SkinSection(),
                SupportInterfaceSection(),
                SupportSection(),
                SkirtSection(),
                OrphanSection());
    }

    Rule IsASection()
    {
        return FirstOf(
                Test(FillSectionNode.designator),
                Test(InnerPerimeterSectionNode.designator),
                Test(OuterPerimeterSectionNode.designator),
                Test(SkinSectionNode.designator),
                Test(SupportInterfaceSectionNode.designator),
                Test(SupportSectionNode.designator),
                Test(SkirtSectionNode.designator));
    }

    // Comment element within a line
    // ;Blah blah blah\n
    @SuppressSubnodes
    Rule Comment(Var<String> commentText)
    {
        return Sequence(
                ZeroOrMore(' '),
                ';',
                OneOrMore(NotNewline()),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        commentText.set(context.getMatch());
                        return true;
                    }
                }
        );
    }

    // ;Blah blah blah\n
    Rule CommentDirective()
    {
        Var<String> commentValue = new Var<>();

        return Sequence(
                TestNot(FillSectionNode.designator),
                TestNot(InnerPerimeterSectionNode.designator),
                TestNot(OuterPerimeterSectionNode.designator),
                TestNot(SkinSectionNode.designator),
                TestNot(SupportSectionNode.designator),
                TestNot(SupportInterfaceSectionNode.designator),
                Comment(commentValue),
                Newline(),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        CommentNode node = new CommentNode(commentValue.get());
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    // M14 or M104
    Rule MCode()
    {
        Var<Integer> mValue = new Var<>();
        Var<Boolean> sPresent = new Var<>();
        Var<Integer> sValue = new Var<>();
        Var<Boolean> tPresent = new Var<>();
        Var<Integer> tValue = new Var<>();

        return Sequence(Sequence(
                        'M', OneToThreeDigits(),
                        mValue.set(Integer.valueOf(match())),
                        Optional(' ')
                ),
                ZeroOrMore(
                        FirstOf(
                                Sequence(
                                        'S',
                                        sPresent.set(true),
                                        Optional(
                                                OneOrMore(Digit()),
                                                sValue.set(Integer.valueOf(match()))
                                        ),
                                        Optional(' ')
                                ),
                                Sequence(
                                        'T',
                                        tPresent.set(true),
                                        Optional(
                                                OneOrMore(Digit()),
                                                tValue.set(Integer.valueOf(match()))
                                        ),
                                        Optional(' ')
                                )
                        )
                ),
                Newline(),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        MCodeNode node = new MCodeNode();
                        node.setMNumber(mValue.get());

                        if (sPresent.isSet() && sValue.isNotSet())
                        {
                            node.setSOnly(true);
                        } else if (sValue.isSet())
                        {
                            node.setSNumber(sValue.get());
                        }

                        if (tPresent.isSet() && tValue.isNotSet())
                        {
                            node.setTOnly(true);
                        } else if (tValue.isSet())
                        {
                            node.setTNumber(tValue.get());
                        }

                        node.setGCodeLineNumber(++currentLineNumber);

                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    // G3 or G12
    Rule GCodeDirective()
    {
        Var<Integer> gcodeValue = new Var<>();

        return Sequence('G', OneOrTwoDigits(),
                gcodeValue.set(Integer.valueOf(match())),
                Newline(),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        GCodeDirectiveNode node = new GCodeDirectiveNode();
                        node.setGValue(gcodeValue.get());
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                });
    }

    //Retract
    // G1 F1800 E-0.50000
    Rule RetractDirective()
    {
        Var<Float> dValue = new Var<>();
        Var<Float> eValue = new Var<>();
        Var<Float> fValue = new Var<>();

        return Sequence("G1 ",
                Optional(
                        Feedrate(fValue)
                ),
                OneOrMore(
                        FirstOf(
                                Sequence("D", NegativeFloatingPointNumber(),
                                        dValue.set(Float.valueOf(match())),
                                        Optional(' ')
                                ),
                                Sequence("E", NegativeFloatingPointNumber(),
                                        eValue.set(Float.valueOf(match())),
                                        Optional(' '))
                        )
                ),
                Newline(),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        RetractNode node = new RetractNode();
                        if (dValue.isSet())
                        {
                            node.getExtrusion().setD(dValue.get());
                        }
                        if (eValue.isSet())
                        {
                            node.getExtrusion().setE(eValue.get());
                        }
                        if (fValue.isSet())
                        {
                            node.getFeedrate().setFeedRate_mmPerMin(fValue.get());
                        }
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    //Unetract
    // G1 F1800 E0.50000
    Rule UnretractDirective()
    {
        Var<Float> dValue = new Var<>();
        Var<Float> eValue = new Var<>();
        Var<Float> fValue = new Var<>();

        return Sequence("G1 ",
                Optional(
                        Feedrate(fValue)
                ),
                OneOrMore(
                        FirstOf(
                                Sequence("D", PositiveFloatingPointNumber(),
                                        dValue.set(Float.valueOf(match())),
                                        Optional(' ')
                                ),
                                Sequence("E", PositiveFloatingPointNumber(),
                                        eValue.set(Float.valueOf(match())),
                                        Optional(' '))
                        )
                ),
                Newline(),
                new Action()
                {
                    @Override
                    public boolean run(Context context
                    )
                    {
                        UnretractNode node = new UnretractNode();
                        if (dValue.isSet())
                        {
                            node.getExtrusion().setD(dValue.get());
                        }
                        if (eValue.isSet())
                        {
                            node.getExtrusion().setE(eValue.get());
                        }
                        if (fValue.isSet())
                        {
                            node.getFeedrate().setFeedRate_mmPerMin(fValue.get());
                        }
                        node.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    //Travel
    // G0 F12000 X88.302 Y42.421 Z1.020
    Rule TravelDirective()
    {
        Var<Float> fValue = new Var<>();
        Var<Double> xValue = new Var<>();
        Var<Double> yValue = new Var<>();

        return Sequence(FirstOf("G0 ","G1 "),
                Optional(
                        Feedrate(fValue)
                ),
                OneOrMore(
                        FirstOf(
                                Sequence("X", 
                                        FloatingPointOrIntegerNumber(),
                                        xValue.set(Double.valueOf(match())),
                                        Optional(' ')
                                ),
                                Sequence("Y", 
                                        FloatingPointOrIntegerNumber(),
                                        yValue.set(Double.valueOf(match())),
                                        Optional(' ')
                                )
                        )
                ),
                Newline(),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        TravelNode node = new TravelNode();

                        if (fValue.isSet())
                        {
                            node.getFeedrate().setFeedRate_mmPerMin(fValue.get());
                        }

                        if (xValue.isSet())
                        {
                            validateXPosition(xValue.get());
                            node.getMovement().setX(xValue.get());
                        }

                        if (yValue.isSet())
                        {
                            validateYPosition(yValue.get());
                            node.getMovement().setY(yValue.get());
                        }

                        node.setGCodeLineNumber(++currentLineNumber);

                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    //Extrusion
    // G1 F840 X88.700 Y44.153 E5.93294
    Rule ExtrusionDirective()
    {
        Var<Float> fValue = new Var<>();
        Var<Double> xValue = new Var<>();
        Var<Double> yValue = new Var<>();
        Var<Double> zValue = new Var<>();
        Var<Float> eValue = new Var<>();
        Var<Float> dValue = new Var<>();

        return Sequence("G1 ",
                Optional(
                        Feedrate(fValue)
                ),
                Optional(
                        Sequence("X", 
                                FloatingPointOrIntegerNumber(),
                                xValue.set(Double.valueOf(match())),
                                ' ',
                                "Y", 
                                FloatingPointOrIntegerNumber(),
                                yValue.set(Double.valueOf(match())),
                                Optional(' '),
                                Optional(Sequence(
                                                "Z", FloatingPointNumber(),
                                                zValue.set(Double.valueOf(match())),
                                                Optional(' ')))
                        )
                ),
                OneOrMore(
                        FirstOf(
                                Sequence("D", PositiveFloatingPointNumber(),
                                        dValue.set(Float.valueOf(match())),
                                        Optional(' ')
                                ),
                                Sequence("E", PositiveFloatingPointNumber(),
                                        eValue.set(Float.valueOf(match())),
                                        Optional(' ')
                                )
                        )
                ),
                Newline(),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        ExtrusionNode node = new ExtrusionNode();

                        if (fValue.isSet())
                        {
                            node.getFeedrate().setFeedRate_mmPerMin(fValue.get());
                        }

                        if (xValue.isSet())
                        {
                            validateXPosition(xValue.get());
                            node.getMovement().setX(xValue.get());
                        }

                        if (yValue.isSet())
                        {
                            validateYPosition(yValue.get());
                            node.getMovement().setY(yValue.get());
                        }

                        if (zValue.isSet())
                        {
                            validateZPosition(zValue.get());
                            node.getMovement().setZ(zValue.get());
                        }

                        if (dValue.isSet())
                        {
                            node.getExtrusion().setD(dValue.get());
                        }

                        if (eValue.isSet())
                        {
                            node.getExtrusion().setE(eValue.get());
                        }

                        node.setGCodeLineNumber(++currentLineNumber);

                        context.getValueStack()
                        .push(node);

                        return true;
                    }
                }
        );
    }

    //Layer change
    //Travel
    // G0 F12000 X88.302 Y42.421 Z1.020
    Rule LayerChangeDirective()
    {
        Var<Float> fValue = new Var<>();
        Var<Double> xValue = new Var<>();
        Var<Double> yValue = new Var<>();
        Var<Double> zValue = new Var<>();

        return Sequence("G0 ",
                Optional(
                        Feedrate(fValue)
                ),
                ZeroOrMore(
                        FirstOf(
                                Sequence("X", FloatingPointOrIntegerNumber(),
                                        xValue.set(Double.valueOf(match())),
                                        Optional(' ')
                                ),
                                Sequence("Y", FloatingPointOrIntegerNumber(),
                                        yValue.set(Double.valueOf(match())),
                                        Optional(' ')
                                )
                        )
                ),
                Sequence("Z", FloatingPointOrIntegerNumber(),
                        zValue.set(Double.valueOf(match())),
                        Optional(' ')
                ),
                Newline(),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        LayerChangeDirectiveNode node = new LayerChangeDirectiveNode();

                        if (fValue.isSet())
                        {
                            node.getFeedrate().setFeedRate_mmPerMin(fValue.get());
                        }

                        if (xValue.isSet())
                        {
                            node.getMovement().setX(xValue.get());
                            validateXPosition(xValue.get());
                        }

                        if (yValue.isSet())
                        {
                            node.getMovement().setY(yValue.get());
                            validateYPosition(yValue.get());
                        }

                        if (zValue.isSet())
                        {
                            node.getMovement().setZ(zValue.get());
                            validateZPosition(zValue.get());
                            currentLayerHeight = zValue.get();
                        }

                        node.setGCodeLineNumber(++currentLineNumber);

                        context.getValueStack().push(node);
                        return true;
                    }
                }
        );
    }

    @SuppressSubnodes
    Rule ChildDirective()
    {
        return FirstOf(CommentDirective(),
                MCode(),
                LayerChangeDirective(),
                GCodeDirective(),
                RetractDirective(),
                UnretractDirective(),
                TravelDirective(),
                ExtrusionDirective(),
                UnrecognisedLine()
        );
    }

    @SuppressSubnodes
    Rule OneOrTwoDigits()
    {
        return FirstOf(TwoDigits(), Digit());
    }

    @SuppressSubnodes
    Rule TwoOrThreeDigits()
    {
        return FirstOf(ThreeDigits(), TwoDigits());
    }

    @SuppressSubnodes
    Rule OneToThreeDigits()
    {
        return FirstOf(ThreeDigits(), TwoDigits(), Digit());
    }

    @SuppressSubnodes
    Rule TwoDigits()
    {
        return Sequence(Digit(), Digit());
    }

    @SuppressSubnodes
    Rule ThreeDigits()
    {
        return Sequence(Digit(), Digit(), Digit());
    }

    @SuppressSubnodes
    Rule Digit()
    {
        return CharRange('0', '9');
    }

    @SuppressSubnodes
    Rule IntegerNumber()
    {
        return Sequence(
                Optional('-'),
                OneOrMore(Digit()));
    }

    @SuppressSubnodes
    Rule FloatingPointNumber()
    {
        return FirstOf(
                NegativeFloatingPointNumber(),
                PositiveFloatingPointNumber()
        );
    }

    @SuppressSubnodes
    Rule PositiveFloatingPointNumber()
    {
        //Positive float e.g. 1.23
        return Sequence(
                OneOrMore(Digit()),
                Ch('.'),
                OneOrMore(Digit()));
    }

    @SuppressSubnodes
    Rule NegativeFloatingPointNumber()
    {
        //Negative float e.g. -1.23
        return Sequence(
                Ch('-'),
                OneOrMore(Digit()),
                Ch('.'),
                OneOrMore(Digit()));
    }
    
    @SuppressSubnodes
    Rule FloatingPointOrIntegerNumber()
    {
        return FirstOf(
                FloatingPointNumber(),
                IntegerNumber()
        );
    }

    @SuppressSubnodes
    Rule Feedrate(Var<Float> feedrate)
    {
        return FirstOf(
                Sequence('F', FloatingPointOrIntegerNumber(),
                        feedrate.set(Float.valueOf(match())),
                        Optional(' '),
                        new Action()
                        {
                            @Override
                            public boolean run(Context context)
                            {
                                feedrateInForce = feedrate.get();
                                return true;
                            }
                        }
                ),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        feedrate.set(feedrateInForce);
                        return true;
                    }
                }
        );
    }

    //Anything else we didn't parse... must always be the last thing we look for
    // blah blah \n
    //we mustn't match a line beginning with T as this is the start of an object
    @SuppressSubnodes
    Rule UnrecognisedLine()
    {
        return Sequence(
                NotASection(),
                OneOrMore(NoneOf("T\n")),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        String line = context.getMatch();
                        UnrecognisedLineNode newNode = new UnrecognisedLineNode(line);
                        newNode.setGCodeLineNumber(++currentLineNumber);
                        context.getValueStack().push(newNode);
                        return true;
                    }
                },
                Newline()
        );
    }

    @SuppressSubnodes
    Rule Newline()
    {
        return Sequence(Ch('\n'),
                new Action()
                {
                    @Override
                    public boolean run(Context context)
                    {
                        return true;
                    }
                });
    }

    @SuppressSubnodes
    Rule NotNewline()
    {
        return NoneOf("\n");
    }
}