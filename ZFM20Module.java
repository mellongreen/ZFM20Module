/*
Class for interfacing with ZFM20Module
Copyright (C) 2015  Mellon Green

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;
import javafx.util.Pair;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ZFM20Module {
    public OutputStream output = null;
    public BufferedInputStream input = null;
    public byte[] reply;
    public byte[] fingerRaw;
    public byte[] charfile;
    public boolean ready;
    public String errmsg;
    public int templatecount;
    public short seclevel;
    public short matchedPage;
    public short matchscore;
    public enum DATATYPE
    {
        IMAGE,
        CHARFILE
    };
    public enum DATAPKGSTATUS
    {
        FOLLOWUP,
        END,
        HANG,
        CORRUPT
    };

    private byte[] pkgbuffer;
    private byte[] databuffer;

    private int defaultBaudrate= 57600;
    private SerialPort port;
    //# of attmept after bad reply.
    private static final int ATTEMPT=5;
    //timeout for reply
    private static final int REPLYWAITTIME = 700;
    //timeout for data pkg
    private static final int DATABREAKWAITTIME = 500;
    private static final int PACKETSIZE = 128;
    //addr of module, 0 = uppermost byte
    private static final byte addr0 = (byte)0xFF;
    private static final byte addr1 = (byte)0xFF;
    private static final byte addr2 = (byte)0xFF;
    private static final byte addr3 = (byte)0xFF;

    public ZFM20Module() throws UnsupportedCommOperationException, IOException, PortInUseException {
        seclevel=0;
        templatecount=0;
        matchedPage=0;
        matchscore=0;
        ready=false;
        Enumeration portlistEnum = CommPortIdentifier.getPortIdentifiers();
        List<String> serialPortsName = new ArrayList<String>();
        List<CommPortIdentifier> serialCommportID = new ArrayList<CommPortIdentifier>();

        while (portlistEnum.hasMoreElements()) {
            CommPortIdentifier x = (CommPortIdentifier) portlistEnum.nextElement();
            if (x.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                serialPortsName.add(x.getName());
                serialCommportID.add(x);
            }
        }
        port = null;
        String portname = null;
        switch (serialPortsName.size()) {
            case 0:
                errmsg = "error: No available serial port detected";
                return;
            case 1:
                portname = serialPortsName.get(0);
                break;
            default:
                GenericDropbox x = new GenericDropbox("Port selection", serialPortsName.toArray(new String[serialPortsName.size()]));
                x.setVisible(true);
                if (x.val.isEmpty()) return;
                else portname = x.val;
                break;
        }
        for(int i=0;i<serialCommportID.size();i++)
        {
            if(!serialCommportID.get(i).getName().equals(portname))continue;
            port = (SerialPort) serialCommportID.get(0).open(getClass().toString(), 2000);
            input = new BufferedInputStream(port.getInputStream());

            output = port.getOutputStream();
            port.setSerialPortParams(defaultBaudrate,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
        }
        ready=true;
    }
    public void genImg() throws IOException, InterruptedException
    {
        byte replycode;
        do
        {
            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3, (byte) 0x01, (byte) 0x00, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x05});
            replycode= getReply(12);
        }
        while (replycode!=0x0);
    }
    //upload image to upper. 8 bit greyscale. last 4 bit zeroed out
    public boolean upImg() throws IOException
    {
        byte replycode;
        byte attempt=0;
        do
        {
            attempt++;
            if(attempt>ATTEMPT)return false;
            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3, (byte) 0x01, (byte) 0x00, (byte) 0x03, (byte) 0x0A,(byte)0x00,(byte)0x0E});
            replycode = getReply(12);
        }while (replycode!=0x0);
        if(getdata(DATATYPE.IMAGE))
        {
            fingerRaw = new byte[databuffer.length];
            System.arraycopy(databuffer, 0, fingerRaw, 0, databuffer.length);
            return true;
        }
        else return false;
    }
    //Actual baudrate = 9600*multiplier
    public boolean setBaud(int multiplier) throws IOException
    {
        return (1<=multiplier && multiplier<=12)?setSysParam((byte) 0x4, (byte)multiplier):false;
    }
    public boolean emptyLib() throws IOException {
        byte replycode;
        byte attempt=0;
        do
        {
            attempt++;
            if(attempt>ATTEMPT)return false;
            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3, (byte) 0x01, (byte) 0x00, (byte) 0x03, (byte) 0x0D,(byte)0x00,(byte)0x11});
            replycode = getReply(12);
        }while (replycode!=0x0);
        return true;
    }
    public boolean img2tz(int charBufferID) throws IOException
    {

        byte x = (byte)charBufferID;
        byte replycode;
        short cksum = getChecksum((byte) 0x1, (byte) 0x4, (byte) 0x2, x);
        byte attempt=0;
        do {
            attempt++;
            if (attempt > ATTEMPT) return false;
            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0, addr1, addr2, addr3, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x02, x, (byte) ((cksum >> 8) & 0xFF), (byte) (cksum & 0xFF)});
            replycode = getReply(12);
            switch (replycode) {
                case 0x0:
                    break;
                case 0x1:
                    errmsg = "error: Package error";
                    break;
                case 0x6:
                    errmsg = "error: Unclear image.";
                    break;
                case 0x7:
                case 0x15:
                    errmsg = "error: Cannot find fingerprint feature";
                    break;
                default:
                    errmsg = "error: Unknown error";
                    break;
            }
        }while(replycode!=0x0);
        return true;
    }
    public boolean getTemplateCount() throws IOException
    {
        byte replycode;
        byte attempt=0;
        do
        {
            attempt++;
            if (attempt > ATTEMPT) return false;
            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3, (byte) 0x01, (byte) 0x00, (byte) 0x03, (byte) 0x1D,(byte) 0x00,(byte) 0x21});
            replycode = getReply(14);

            switch (replycode)
            {
                case 0x0:
                    break;
                case 0x1:
                    errmsg = "error: Package error";
                    break;
                default:
                    errmsg = "error: Unknown error";
                    break;
            }
        }while (replycode!=0x0);

        templatecount = (int)(reply[10]<<8);
        templatecount = (int)(templatecount^(reply[11]&0xFF));
        return true;
    }
    public boolean store(int charBufferID,int pageID) throws IOException
    {
        byte x = (byte)charBufferID;
        short y = (short)pageID;
        short cksum = getChecksum((short) 0x1, (short) 0x6, (short) 0x6, (short)charBufferID,(short)pageID);
        byte replycode;
        byte attempt=0;
        do
        {
            attempt++;
            if(attempt>ATTEMPT)return false;
            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3, (byte) 0x01, (byte) 0x00, (byte) 0x06, (byte) 0x06,x,(byte) ((y >> 8) & 0xFF), (byte) (y & 0xFF),(byte) ((cksum >> 8) & 0xFF), (byte) (cksum & 0xFF)});
            replycode = getReply(12);
        }while (replycode!=0x0);
        return true;
    }
    public boolean readSysParam() throws IOException {
        byte replycode;
        byte attempt=0;
        do
        {
            attempt++;
            if(attempt>ATTEMPT)return false;
            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3, (byte) 0x01, (byte) 0x00, (byte) 0x03, (byte) 0x0F,(byte)0x00,(byte)0x13});

            replycode = getReply(28);
        }while (replycode!=0x0);
        seclevel = (short)((reply[16]<<8)&0xFF00|(reply[17]<<0)&0x00FF);
        return true;
    }
    public boolean regmodel() throws IOException {
        byte replycode;
        output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0, addr1, addr2, addr3, (byte) 0x01, (byte) 0x00, (byte) 0x03, (byte) 0x05, (byte) 0x0,(byte)0x9});
        replycode = getReply(12);
        switch (replycode)
        {
            case 0x0:
                break;
            case 0x1:
                errmsg = "error: Package error";
                break;
            case 0xA:
                errmsg = "error: Fingerprints do not match";
                break;
            default:
                errmsg = "error: Unknown error";
                break;
        }
        return replycode==0x0?true:false;

    }
    public boolean search(int charBufferID,int pages) throws IOException {
        byte x = (byte)charBufferID;
        short y = (short)pages;
        short cksum = getChecksum((short) 0x1, (short) 0x8, (byte) 0x4, (short)charBufferID,(short)0x0,(short)pages);
        byte replycode;
        byte attempt=0;
        do
        {
            attempt++;
            if(attempt>ATTEMPT)
                return false;
            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3, (byte) 0x01, (byte) 0x00, (byte) 0x08, (byte) 0x04,x,(byte)0x0,(byte)0x0,(byte) ((y >> 8) & 0xFF), (byte) (y & 0xFF),(byte) ((cksum >> 8) & 0xFF), (byte) (cksum & 0xFF)});
            replycode = getReply(16);
        }while (replycode!=0x0 && replycode!=0x9);
        if(replycode==0x9)
        {
            errmsg = "error: no match found";
            return false;
        }
        matchedPage = (short)((reply[10]<<8)&0xFF00|(reply[11]<<0)&0x00FF);
        matchscore = (short)((reply[12]<<8)&0xFF00|(reply[13]<<0)&0x00FF);
        return true;
    }
    public boolean upchar(int charBufferID) throws IOException {
        byte x = (byte)charBufferID;
        short cksum = getChecksum((byte) 0x1, (byte) 0x4, (byte) 0x8, x);

        byte replycode;
        byte attempt=0;
        do
        {
            attempt++;
            if(attempt>ATTEMPT)return false;

            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x08,x,(byte) ((cksum >> 8) & 0xFF), (byte) (cksum & 0xFF)});

            replycode = getReply(12);

        }while (replycode!=0x0);

        if(getdata(DATATYPE.CHARFILE))
        {
            charfile = new byte[databuffer.length];
            System.arraycopy(databuffer, 0, charfile, 0, databuffer.length);
            return true;
        }
        else return false;
    }
    public void close() throws Exception
    {
        output.close();
        input.close();
        port.close();
    }
    private boolean getdata(DATATYPE type) throws IOException
    {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        DATAPKGSTATUS stat;
        boolean term = true;
        while (term)
        {
            stat= getdatapkg(type);
            switch (stat)
            {
                case FOLLOWUP:
                    data.write(pkgbuffer);
                    break;
                case END:
                    data.write(pkgbuffer);
                    databuffer=data.toByteArray();
                    term=false;
                    break;
                case HANG:
                    errmsg = "error: data transfer hang";
                case CORRUPT:
                    errmsg = "error: corrupt data";
                    return false;
            }
        }
        return true;
    }
    private Pair<Integer,DATAPKGSTATUS> consumeheader() throws IOException {
        byte nextExpectedByte = (byte)0xEF;
        int count =0;
        short length=0;
        DATAPKGSTATUS stat = DATAPKGSTATUS.CORRUPT;
        long lastreceived = System.currentTimeMillis();
        while(true)
        {
            if (input.available() > 0)
            {
                while(input.available()>0)
                {
                    byte in = (byte)input.read();
                    count++;
                    if(count<=6)
                    {
                        if (in != nextExpectedByte)
                            return new Pair<Integer, DATAPKGSTATUS>(-1, DATAPKGSTATUS.CORRUPT);
                        switch (count)
                        {
                            case 1:
                                nextExpectedByte = (byte)0x1;
                                break;
                            case 2:
                                nextExpectedByte = addr0;
                                break;
                            case 3:
                                nextExpectedByte = addr1;
                                break;
                            case 4:
                                nextExpectedByte = addr2;
                                break;
                            case 5:
                                nextExpectedByte = addr3;
                                break;
                            default:
                                break;
                        }
                    }
                    else
                    {
                        switch (count)
                        {
                            case 7:
                                if(in==(byte)0x2)stat = DATAPKGSTATUS.FOLLOWUP;
                                else if(in==(byte)0x8)stat = DATAPKGSTATUS.END;
                                else return new Pair<Integer, DATAPKGSTATUS>(-1, DATAPKGSTATUS.CORRUPT);
                                break;
                            case 8:
                                length^=in;
                                length = (short)(length<<8);
                                break;
                            case 9:
                                length= (short)(length^(in&0xFF));
                                return new Pair<Integer, DATAPKGSTATUS>((int) length,stat);
                            default:
                                break;
                        }
                    }
                }
                lastreceived = System.currentTimeMillis();
            }
            else
            {
                if(System.currentTimeMillis()-lastreceived> DATABREAKWAITTIME)return new Pair<Integer, DATAPKGSTATUS>(-1, DATAPKGSTATUS.HANG);
            }
        }

    }
    private DATAPKGSTATUS getdatapkg(DATATYPE type) throws IOException
    {
        Pair<Integer,DATAPKGSTATUS> headerstat = consumeheader();
        int discardtarget = 2;
        int readtarget = headerstat.getKey()-discardtarget;
        switch (headerstat.getValue())
        {
            case HANG:
            case CORRUPT:
                return headerstat.getValue();
            default:
                break;
        }
        int readcount=0,discardcount=0;
        if(readtarget<=0)return headerstat.getValue();

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        long lastreceived = System.currentTimeMillis();
        while(true)
        {
            if (input.available() > 0)
            {
                while(input.available()>0)
                {
                    byte in = (byte) input.read();
                    readcount++;
                    if (readcount <= readtarget)
                    {
                        if (type == DATATYPE.IMAGE)
                        {
                            data.write((byte) (in & (byte) 0xF0));
                            data.write((byte) ((in & (byte) 0x0F) << 4));
                        }
                        else
                        {
                            data.write(in);
                        }
                    }
                    else
                    {
                        if(discardtarget<=1)
                        {
                            pkgbuffer = data.toByteArray();
                            return headerstat.getValue();
                        }
                        else discardtarget--;
                    }
                }
                lastreceived = System.currentTimeMillis();
            }
            else
            {
                if(System.currentTimeMillis()-lastreceived> DATABREAKWAITTIME)
                    return DATAPKGSTATUS.HANG;
            }
        }

    }
    private boolean setSysParam(byte paramNum,byte val) throws IOException
    {
        short cksum = getChecksum((byte) 0x1, (byte) 0x5, (byte) 0xE, paramNum, val);
        byte replycode;
        byte attempt=0;
        do
        {
            attempt++;
            if(attempt>ATTEMPT)return false;
            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3, (byte) 0x01, (byte) 0x00, (byte) 0x05, (byte) 0x0E, paramNum, val, (byte) ((cksum >> 8) & 0xFF), (byte) (cksum & 0xFF)});
            replycode = getReply(12);
        }
        while (replycode!=0x0);
        return true;
    }
    private short getChecksum(byte[] data,int begin,int length,byte... aux)
    {
        short ret=0;
        for (int i = 0; i < length; i++)
        {
            short arg = (short)(data[begin+i]);
            while (arg != 0)
            {
                short c = (short)(ret & arg);
                ret ^= arg;
                arg = (short)(c << 1);
            }
        }
        for(byte x: aux)
        {
            short arg = (short)x;
            while (arg != 0)
            {
                short c = (short)(ret & arg);
                ret ^= arg;
                arg = (short)(c << 1);
            }
        }
        return ret;
    }
    private short getChecksum(byte... args)
    {
        short ret=0;
        for(byte x:args)
        {
            short arg = (short)x;
            while (arg != 0)
            {
                short c = (short)(ret & arg);
                ret ^= arg;
                arg = (short)(c << 1);
            }
        }
        return ret;
    }
    private short getChecksum(short... args)
    {
        short ret=0;
        for(short x:args)
        {
            while (x != 0)
            {
                short c = (short)(ret & x);
                ret ^= x;
                x = (short)(c << 1);
            }
        }
        return ret;
    }
    private byte getReply(int len) throws IOException {
        int count = 0;
        ByteBuffer buf = ByteBuffer.allocate(len);
        long start = System.currentTimeMillis();
        while(count!=len)
        {
            if(System.currentTimeMillis()-start> REPLYWAITTIME)
                return 0x1;
            while(input.available()>0 && count!=len)
            {
                count++;
                buf.put((byte)input.read());
            }
        }
        reply=buf.array();
        return reply[9];
    }

    //dont use these
    public boolean downchar(int charBufferID,byte[] data) throws IOException {
        byte x = (byte)charBufferID;
        byte replycode;
        short cksum = getChecksum((byte) 0x1, (byte) 0x4, (byte) 0x9, x);
        byte attempt=0;
        do
        {
            attempt++;
            if(attempt>ATTEMPT)return false;
            output.write(new byte[]{(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x09,x,(byte) ((cksum >> 8) & 0xFF), (byte) (cksum & 0xFF)});
            replycode = getReply(12);
        }while (replycode!=0x0);
        //ready to accept
        return writepkg(data,(byte) 0xEF, (byte) 0x01, addr0,addr1,addr2,addr3);
    }
    private boolean writepkg(byte[] data,byte... prefix) throws IOException
    {
        if(data.length<=0)return false;
        int remainingbyte = data.length,current=0;
        short packetlength=0,ck=0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        remainingbyte-=PACKETSIZE;
        boolean term =false;
        while (true)
        {
            out.reset();
            out.write(prefix);
            if(remainingbyte<=0)
            {
                term=true;
                out.write((byte)0x8);
                packetlength = (short) (remainingbyte+PACKETSIZE+2);
            }
            else
            {
                out.write((byte)0x2);
                packetlength = PACKETSIZE+2;
            }

            ck = getChecksum(data,current,packetlength-2,(byte) ((packetlength >> 8) & 0xFF),(byte)(packetlength & 0xFF));

            out.write((byte) ((packetlength >> 8) & 0xFF));
            out.write((byte) (packetlength & 0xFF));
            out.write(data);
            out.write((byte) ((ck >> 8) & 0xFF));
            out.write((byte) (ck & 0xFF));
            output.write(out.toByteArray());
            if(term)break;
            current+=packetlength-2;
            remainingbyte-=PACKETSIZE;
        }
        out.close();
        return true;
    }
}
