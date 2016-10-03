package celtech.roboxbase.importers.twod.svg;

/**
 *
 * @author ianhudson
 */
public class SVGConverterConfiguration
{

    private static SVGConverterConfiguration instance = null;

    private int travelFeedrate = 4000;
    private int cuttingFeedrate = 2000;
    private int plungeFeedrate = 200;
    private int plungeDepth = 0;
    private int liftDepth = 10;
    private float xPointCoefficient = 1;
    private float yPointCoefficient = 1;

    private SVGConverterConfiguration()
    {
    }

    public static SVGConverterConfiguration getInstance()
    {
        if (instance == null)
        {
            instance = new SVGConverterConfiguration();
        }

        return instance;
    }

    public void setCuttingFeedrate(int cuttingFeedrate)
    {
        this.cuttingFeedrate = cuttingFeedrate;
    }

    public int getCuttingFeedrate()
    {
        return cuttingFeedrate;
    }

    public int getPlungeFeedrate()
    {
        return plungeFeedrate;
    }

    public void setPlungeFeedrate(int plungFeedrate)
    {
        this.plungeFeedrate = plungFeedrate;
    }

    public void setPlungeDepth(int plungeDepth)
    {
        this.plungeDepth = plungeDepth;
    }

    public int getPlungeDepth()
    {
        return plungeDepth;
    }

    public void setLiftDepth(int liftDepth)
    {
        this.liftDepth = liftDepth;
    }

    public int getLiftDepth()
    {
        return liftDepth;
    }

    public void setTravelFeedrate(int travelFeedrate)
    {
        this.travelFeedrate = travelFeedrate;
    }

    public int getTravelFeedrate()
    {
        return travelFeedrate;
    }

    public void setxPointCoefficient(float xPointCoefficient)
    {
        this.xPointCoefficient = xPointCoefficient;
    }

    public float getxPointCoefficient()
    {
        return xPointCoefficient;
    }

    public void setyPointCoefficient(float yPointCoefficient)
    {
        this.yPointCoefficient = yPointCoefficient;
    }

    public float getyPointCoefficient()
    {
        return yPointCoefficient;
    }
}
