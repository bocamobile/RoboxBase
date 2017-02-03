package celtech.roboxbase.comms.remote;

import celtech.roboxbase.comms.RemoteDetectedPrinter;
import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.rx.RoboxRxPacket;
import celtech.roboxbase.comms.tx.RoboxTxPacket;
import celtech.roboxbase.postprocessor.PrintJobStatistics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;

/**
 *
 * @author ianhudson
 */
public class RemoteClient implements LowLevelInterface
{

    private final Stenographer steno = StenographerFactory.getStenographer(RemoteClient.class.getName());
    private final RemoteDetectedPrinter remotePrinterHandle;

    private final String baseAPIString;
    private final String connectUrlString;
    private final String disconnectUrlString;
    private final String writeToPrinterUrlString;
    private final ObjectMapper mapper = new ObjectMapper();

    public RemoteClient(RemoteDetectedPrinter remotePrinterHandle)
    {
        this.remotePrinterHandle = remotePrinterHandle;
        baseAPIString = "/api";
        connectUrlString = Configuration.lowLevelAPIService + Configuration.connectService;
        disconnectUrlString = Configuration.lowLevelAPIService + Configuration.disconnectService;
        writeToPrinterUrlString = Configuration.lowLevelAPIService + Configuration.writeDataService;
    }

    @Override
    public boolean connect(String printerID) throws RoboxCommsException
    {
        boolean success = false;
        try
        {
            remotePrinterHandle.getServerPrinterIsAttachedTo().postRoboxPacket(baseAPIString + "/" + printerID + connectUrlString);
        } catch (IOException ex)
        {
            steno.error("Failed to connect to remote printer " + remotePrinterHandle);
            throw new RoboxCommsException("Failed to connect to remote printer " + remotePrinterHandle);
        }
        return success;
    }

    @Override
    public void disconnect(String printerID) throws RoboxCommsException
    {
        try
        {
            remotePrinterHandle.getServerPrinterIsAttachedTo().postRoboxPacket(baseAPIString + "/" + printerID + disconnectUrlString);
        } catch (IOException ex)
        {
            steno.error("Failed to disconnect from remote printer " + remotePrinterHandle);
            throw new RoboxCommsException("Failed to disconnect from remote printer " + remotePrinterHandle);
        }
    }

    @Override
    public RoboxRxPacket writeToPrinter(String printerID, RoboxTxPacket messageToWrite) throws RoboxCommsException
    {
        RoboxRxPacket returnedPacket = null;

        try
        {
            String dataToOutput = mapper.writeValueAsString(messageToWrite);
            returnedPacket = remotePrinterHandle.getServerPrinterIsAttachedTo().postRoboxPacket(baseAPIString + "/" + printerID + writeToPrinterUrlString, dataToOutput, RoboxRxPacket.class);
        } catch (IOException ex)
        {
            steno.error("Failed to write to remote printer (" + messageToWrite.getPacketType().name() + ") " + remotePrinterHandle);
            throw new RoboxCommsException("Failed to write to remote printer (" + messageToWrite.getPacketType().name() + ") " + remotePrinterHandle);
        }

        return returnedPacket;
    }
}
