import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.Animator;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

/** ImageJ Plugin for reading an AVI file into an image stack (one slice per video frame) */
/*
 *  Version 2008-04-25 by Michael Schmid, based on a plugin by
 *  Daniel Marsh and Wayne Rasband
 *
 * Restrictions and Notes:
 *      - Only few formats supported:
 *          - uncompressed 8 bit with palette (=LUT)
 *          - uncompressed 8 & 16 bit grayscale
 *          - uncompressed 24 & 32 bit RGB (alpha channel ignored)
 *          - uncompressed 32 bit AYUV (alpha channel ignored)
 *          - various YUV 4:2:2 compressed formats
 *      - Does not read avi formats with more than one frame per chunk
 *      - Palette changes during the video not supported
 *      - Out-of-sequence frames (sequence given by index) not supported
 *      - Different frame sizes in one file (rcFrame) not supported
 *      - Conversion of (A)YUV formats to grayscale is non-standard:
 *        All 255 levels are kept as in the input (i.e. the full dynamic
 *        range of data from a frame grabber is preserved).
 *        For standard behavior, use "Brightness&Contrast", Press "Set",
 *        enter "Min." 16, "Max." 235, and press "Apply".
 * Enhancements with respect to the previous version:
 *      - support for several other formats added, especially some YUV
 *        (also named YCbCr) formats
 *      - uneven chunk sizes fixed
 *      - negative biHeight fixed  
 *      - audio or second video stream don't cause a problem
 *      - can read part of a file (specify start & end frame numbers)
 *      - can convert YUV and RGB to grayscale (does not convert 8-bit with palette)
 *      - can flip vertically
 *      - can create a virtual stack
 *      - added slice label: time of the frame in the movie
 *      - added a public method 'getStack' that does not create an image window
 *      - more compact code, especially for reading the header (rewritten)
 *      - in the code, bitmapinfo items have their canonical names
 *
 * The AVI format looks like this:
 * RIFF                     RIFF HEADER
 * |-AVI                    AVI CHUNK  
 *   |LIST hdrl             MAIN AVI HEADER
 *   | |-avih               AVI HEADER
 *   | |LIST strl           STREAM LIST(s) (One per stream)
 *   | | |-strh             STREAM HEADER (Required after above; fourcc type is 'vids' for video stream)
 *   | | |-strf             STREAM FORMAT (for video: BitMapInfo; may also contain palette)
 *   | | |-strd             OPTIONAL -- STREAM DATA (ignored in this plugin)
 *   | | |-strn             OPTIONAL -- STREAM NAME (ignored in this plugin)
 *   |LIST movi             MOVIE DATA
 *   | | [rec]              RECORD DATA (one record per frame for interleaved video; optional, unsupported in this plugin)
 *   | |  |-dataSubchunks   RAW DATA: '??wb' for audio, '??db' and '??dc' for uncompressed and
 *   |                      compressed video, respectively. "??" denotes stream number, usually "00" or "01"
 *   |-idx1                 AVI INDEX (required by some programs, ignored in this plugin)

 */

public class AVI_Reader extends VirtualStack implements PlugIn {

    //four-character codes for avi chunk types
    //NOTE: byte sequence is reversed - ints in Intel (little endian) byte order!
    private final static int   FOURCC_RIFF = 0x46464952;   //'RIFF'
    private final static int   FOURCC_AVI =  0x20495641;   //'AVI '
    private final static int   FOURCC_LIST = 0x5453494c;   //'LIST'
    private final static int   FOURCC_hdrl = 0x6c726468;   //'hdrl'
    private final static int   FOURCC_avih = 0x68697661;   //'avih'
    private final static int   FOURCC_strl = 0x6c727473;   //'strl'
    private final static int   FOURCC_strh = 0x68727473;   //'strh'
    private final static int   FOURCC_strf = 0x66727473;   //'strf'
    private final static int   FOURCC_movi = 0x69766f6d;   //'movi'
    private final static int   FOURCC_rec =  0x20636572;   //'rec '
    private final static int   FOURCC_JUNK = 0x4b4e554a;   //'JUNK'
    private final static int   FOURCC_vids = 0x73646976;   //'vids'
    private final static int   FOURCC_00db = 0x62643030;   //'00db'
    private final static int   FOURCC_00dc = 0x63643030;   //'00dc'
    //four-character codes for supported compression formats; see fourcc.org
    private final static int   NO_COMPRESSION    = 0;          //uncompressed, also 'RGB ', 'RAW '
    private final static int   NO_COMPRESSION_RGB= 0x20424752; //'RGB ' -a name for uncompressed
    private final static int   NO_COMPRESSION_RAW= 0x20574152; //'RAW ' -a name for uncompressed
    private final static int   NO_COMPRESSION_Y800=0x30303859;//'Y800' -a name for 8-bit grayscale
    private final static int   NO_COMPRESSION_Y8 = 0x20203859; //'Y8  ' -another name for Y800
    private final static int   NO_COMPRESSION_GREY=0x59455247;//'GREY' -another name for Y800
    private final static int   NO_COMPRESSION_Y16= 0x20363159; //'Y16 ' -a name for 16-bit uncompressed grayscale
    private final static int   AYUV_COMPRESSION  = 0x56555941; //'AYUV' -uncompressed, but alpha, Y, U, V bytes
    private final static int   UYVY_COMPRESSION  = 0x59565955; //'UYVY' - 4:2:2 with byte order u y0 v y1
    private final static int   Y422_COMPRESSION  = 0x564E5955; //'Y422' -another name for of UYVY
    private final static int   UYNV_COMPRESSION  = 0x32323459; //'UYNV' -another name for of UYVY
    private final static int   CYUV_COMPRESSION  = 0x76757963; //'cyuv' -as UYVY but not top-down
    private final static int   V422_COMPRESSION  = 0x32323456; //'V422' -as UYVY but not top-down
    private final static int   YUY2_COMPRESSION  = 0x32595559; //'YUY2' - 4:2:2 with byte order y0 u y1 v
    private final static int   YUNV_COMPRESSION  = 0x564E5559; //'YUNV' -another name for YUY2
    private final static int   YUYV_COMPRESSION  = 0x56595559; //'YUYV' -another name for YUY2
    private final static int   YVYU_COMPRESSION  = 0x55595659; //'YVYU' - 4:2:2 with byte order y0 u y1 v
    
    private final static int   BITMASK24 = 0x10000;     //for 24-bit (in contrast to 8, 16,... not a bitmask)
    private final static long  SIZE_MASK = 0xffffffffL; //for conversion of sizes from unsigned int to long

    //dialog parameters are static so they will be remembered
    private static int         firstFrameNumber = 1; //the first frame to read
    private static int         lastFrameNumber = 0;  //the last frame to read; 0 means 'read all'
    private static boolean     convertToGray;        //whether to convert color video to grayscale
    private static boolean     flipVertical;         //whether to flip image vertical
    private static boolean     isVirtual;            //whether to open as virtual stack
    //the input file
    private  RandomAccessFile  raFile;
    private  boolean           headerOK = false;    //whether header has been read
    //more avi file properties etc
    private  int               streamNumber;        //number of the (first) video stream
    private  long              fileSize = 0;        //file size (for progress bar)
    private  int               paddingGranularity = 2;  //tags start at even address
    //derived from BitMapInfo
    private  int               dataCompression;     //data compression type used
    private  int               scanLineSize;
    private  boolean           dataTopDown;         //whether data start at top of image
    private  ColorModel        cm;
    //for conversion to ImageJ stack
    private  Vector            frameInfos;  //for virtual stack: long[] with frame pos&size in file, time(usec)
    private  ImageStack        stack;
    //for debug messages
    private  boolean           verbose = IJ.debugMode;
    private  long              startTime;


    //From AVI Header Chunk
    private  int               dwMicroSecPerFrame;
    private  int               dwMaxBytesPerSec;
    private  int               dwReserved1;
    private  int               dwFlags;
    private  int               dwTotalFrames;
    private  int               dwInitialFrames;
    private  int               dwStreams;
    private  int               dwSuggestedBufferSize;
    private  int               dwWidth;
    private  int               dwHeight;
    private  int               dwScale;
    private  int               dwRate;
    private  int               dwStart;
    private  int               dwLength;

    //From Stream Header Chunk
    private  int               fccStreamHandler;
    private  int               dwStreamFlags;
    private  int               dwStreamReserved1;
    private  int               dwStreamInitialFrames;
    private  int               dwStreamScale;
    private  int               dwStreamRate;
    private  int               dwStreamStart;
    private  int               dwStreamLength;
    private  int               dwStreamSuggestedBufferSize;
    private  int               dwStreamQuality;
    private  int               dwStreamSampleSize;

    //From Stream Format Chunk: BITMAPINFO contents (40 bytes)
    private  int               biSize;              // size of this header in bytes (40)
    private  int               biWidth;
    private  int               biHeight;
    private  short             biPlanes;            // no. of color planes: for the formats decoded, always 1
    private  short             biBitCount;          // Bits per Pixel
    private  int               biCompression;
    private  int               biSizeImage;         // size of image in bytes (may be 0: if so, calculate)
    private  int               biXPelsPerMeter;     // horizontal resolution, pixels/meter (may be 0)
    private  int               biYPelsPerMeter;     // vertical resolution, pixels/meter (may be 0)
    private  int               biClrUsed;           // no. of colors in palette (if 0, calculate)
    private  int               biClrImportant;      // no. of important colors (appear first in palette) (0 means all are important)


    /** The plugin is invoked by ImageJ using this method.
     *  String 'arg' may be used to select the path.
     */
    public void run (String arg) {
        OpenDialog  od = new OpenDialog("Select AVI File", arg);    //file dialog
        String fileName = od.getFileName();
        if (fileName == null) return;
        String fileDir = od.getDirectory();
        String path = fileDir + fileName;
        try {
            openAndReadHeader(path);                                //open and read header
        } catch (Exception e) {
            showExceptionMessage(e);
            return;
        }
        if (!showDialog(fileName)) return;                          //ask for parameters
        ImageStack stack = makeStack (path, firstFrameNumber, lastFrameNumber,
                isVirtual, convertToGray, flipVertical);            //read data
        if (stack == null) return;
        if (stack.getSize() == 0) {
            String rangeText = "";
            if (firstFrameNumber>1 || lastFrameNumber!=0)
                rangeText = "\nin Range "+firstFrameNumber+
                    (lastFrameNumber>0 ? " - "+lastFrameNumber : " - end");
            IJ.showMessage("AVI Reader","Error: No Frames Found"+rangeText);
            return;
        }
        ImagePlus imp = new ImagePlus(fileName, stack);
        imp.show();                                                 //show it
        IJ.showTime(imp, startTime, "Read AVI in ", stack.getSize());
    }


    /** Create an ImageStack from an avi file with given path.
     * @param path              Directoy+filename of the avi file
     * @param firstFrameNumber  Number of first frame to read (first frame of the file is 1)
     * @param lastFrameNumber   Number of last frame to read or 0 for reading all, -1 for all but last...
     * @param isVirtual         Whether to return a virtual stack
     * @param convertToGray     Whether to convert color images to grayscale
     * @return  Returns the stack; null on failure.
     *  The stack returned may be non-null, but have a length of zero if no suitable frames were found
     */
    public ImageStack makeStack (String path, int firstFrameNumber, int lastFrameNumber,
            boolean isVirtual, boolean convertToGray, boolean flipVertical) {
        this.firstFrameNumber = firstFrameNumber;
        this.lastFrameNumber = lastFrameNumber;
        this.isVirtual = isVirtual;
        this.convertToGray = convertToGray;
        this.flipVertical = flipVertical;
        IJ.showProgress(.001);
        try {
            readAVI(path);
        } catch (OutOfMemoryError e) {
            stack.trim();
            IJ.showMessage("AVI Reader", "Out of memory.  " + stack.getSize() + " of " + dwTotalFrames + " frames will be opened.");
        } catch (Exception e) {
            showExceptionMessage(e);
        } finally {
            if (!isVirtual) try {
                raFile.close();
                if (verbose)
                    IJ.log("File closed.");
            } catch (Exception e) {}
            IJ.showProgress(1.0);
        }
        if (isVirtual && frameInfos != null)
            stack = this;
        if (stack != null)
            stack.setColorModel(cm);
        return stack;
    }

    /** Returns an ImageProcessor for the specified slice of this virtual stack (if it is one)
        where 1<=n<=nslices. Returns null if no virtual stack or no slices.
    */
    public ImageProcessor getProcessor(int n) {
        if (frameInfos==null || frameInfos.size()==0) return null;
        Object pixels;
        if (n<1 || n>frameInfos.size())
            throw new IllegalArgumentException("Argument out of range: "+n);
        try {
            long[] frameInfo = (long[])(frameInfos.get(n-1));
            pixels = readFrame(frameInfo[0], (int)frameInfo[1]);
        } catch (Exception e) {
            showExceptionMessage(e);
            return null;
        }
        if (biBitCount <= 8 || convertToGray)
            return new ByteProcessor(biWidth, biHeight, (byte[])pixels, cm);
        else
            return new ColorProcessor(biWidth, biHeight, (int[])pixels);

    }
 
    /** Returns the image width */
    public int getWidth() {
        return biWidth;
    }

    /** Returns the image height */
    public int getHeight() {
        return biHeight;
    }

    /** Returns the number of images in this virtual stack (if it is one) */
    public int getSize() {
        if (frameInfos == null) return 0;
        else return frameInfos.size();
    }

    /** Returns the label of the specified slice in this virtual stack (if it is one). */
    public String getSliceLabel(int n) {
        if (frameInfos==null || n<1 || n>frameInfos.size())
            throw new IllegalArgumentException("No Virtual Stack or argument out of range: "+n);
        return frameLabel(((long[])(frameInfos.get(n-1)))[2]);
    }

    /** Deletes the specified image from this virtual stack (if it is one),
     *  where 1<=n<=nslices. */
    public void deleteSlice(int n) {
        if (frameInfos==null || frameInfos.size()==0) return;
        if (n<1 || n>frameInfos.size())
            throw new IllegalArgumentException("Argument out of range: "+n);
        frameInfos.removeElementAt(n-1);
    }

    /** Parameters dialog, returns false on cancel */
    private boolean showDialog (String fileName) {
        GenericDialog gd = new GenericDialog("AVI Reader...");
        gd.addMessage(fileName+": "+dwTotalFrames+" Frames");
        gd.addNumericField("First Frame: ", firstFrameNumber, 0);
        gd.addNumericField("Last Frame: ", lastFrameNumber, 0, 6, "*");
        gd.addMessage("* also accepts 0 for end, -1 for 'end-1', etc.");
        gd.addCheckbox("Use_Virtual Stack", isVirtual);
        gd.addCheckbox("Convert to Gray", convertToGray);
        gd.addCheckbox("Flip Vertical", flipVertical);
        gd.showDialog();
        if (gd.wasCanceled()) return false;
        firstFrameNumber = (int)gd.getNextNumber();
        lastFrameNumber = (int)gd.getNextNumber();
        isVirtual = gd.getNextBoolean();
        convertToGray = gd.getNextBoolean();
        flipVertical = gd.getNextBoolean();
        IJ.register(this.getClass());
        return true;
    }

    private void readAVI(String path) throws Exception, IOException {
        if (!headerOK)                          //we have not read the header yet?
            openAndReadHeader(path);
        startTime += System.currentTimeMillis();//taking previously elapsed time into account
        findFourccAndRead(FOURCC_movi, true, fileSize, true); //read movie data
        return;
    }

    /** Open the file with given path and read its header */
    private void openAndReadHeader (String path) throws Exception, IOException {
        startTime = System.currentTimeMillis();
        if (verbose)
            IJ.log("OPEN AND READ AVI FILE HEADER "+timeString());
        File file = new File(path);                         // o p e n
        raFile = new RandomAccessFile(file, "r");
        fileSize = raFile.length();
        int fileType = readInt();                           // f i l e   h e a d e r
        if (verbose)
            IJ.log("File header: File type='"+fourccString(fileType)+"' (should be 'RIFF')"+timeString());
        if (fileType != FOURCC_RIFF)
            throw new Exception("Not an AVI file.");
        readInt(); //data size
        int riffType = readInt();
        if (verbose)
            IJ.log("File header: RIFF type='"+fourccString(riffType)+"' (should be 'AVI ')");
        if (riffType != FOURCC_AVI)
            throw new Exception("Not an AVI file.");
        findFourccAndRead(FOURCC_hdrl, true, fileSize, true);
        startTime -= System.currentTimeMillis(); //becomes minus elapsed Time
        headerOK = true;
    }

    /** Find the next position of fourcc or LIST fourcc and read contents.
     *  Returns next position
     *  If not found, throws exception or returns -1 */
    private long findFourccAndRead(int fourcc, boolean isList, long endPosition,
            boolean throwNotFoundException) throws Exception, IOException {
        long nextPos;
        boolean contentOk = false;
        do {
            int type = readType(endPosition);
            if (type == 0) {            //reached endPosition without finding
                if (throwNotFoundException)
                    throw new Exception("Required item '"+fourccString(fourcc)+"' not found");
                else
                    return -1;
            }
            long size = readInt() & SIZE_MASK;
            nextPos = raFile.getFilePointer() + size;
            boolean foundList = false;
            if (isList && type == FOURCC_LIST) {
                foundList = true;
                type = readInt();
            }
            if (verbose)
                IJ.log("Searching for '"+fourccString(fourcc)+"', found "+(foundList?"LIST '":"'")
                    +fourccString(type)+"' "+posSizeString(nextPos-size, size));
            if (type==fourcc) {
                contentOk = readContents(fourcc, nextPos);
            } else if (verbose)
                IJ.log("Discarded '"+fourccString(fourcc)+"': Contents does not fit");
            raFile.seek(nextPos);
            if (contentOk)
                return nextPos;     //found and read, breaks the loop
        } while (!contentOk);
        return nextPos;
    }

    /** read contents defined by four-cc code; returns true if contens ok */
    private boolean readContents (int fourcc, long endPosition) throws Exception, IOException {
        switch (fourcc) {
            case FOURCC_hdrl:
                findFourccAndRead(FOURCC_avih, false, endPosition, true);
                findFourccAndRead(FOURCC_strl, true, endPosition, true);
                return true;
            case FOURCC_avih:
                readAviHeader();
                return true;
            case FOURCC_strl:
                long nextPosition = findFourccAndRead(FOURCC_strh, false, endPosition, false);
                if (nextPosition<0) return false;
                findFourccAndRead(FOURCC_strf, false, endPosition, true);
                return true;
            case FOURCC_strh:
                int streamType = readInt();
                if (streamType != FOURCC_vids) {
                    if (verbose)
                        IJ.log("Non-video Stream '"+fourccString(streamType)+" skipped");
                    streamNumber++;
                    return false;
                }
                readStreamHeader();
                return true;
            case FOURCC_strf:
                readBitMapInfo(endPosition);
                return true;
            case FOURCC_movi:
                readMovieData(endPosition);
                return true;
        }
        throw new Exception("Program error, type "+fourccString(fourcc));
    }

    void readAviHeader() throws Exception, IOException {
        dwMicroSecPerFrame = readInt();
        dwMaxBytesPerSec = readInt();
        dwReserved1 = readInt(); //in newer avi formats, this is dwPaddingGranularity?
        dwFlags = readInt();
        dwTotalFrames = readInt();
        dwInitialFrames = readInt();
        dwStreams = readInt();
        dwSuggestedBufferSize = readInt();
        dwWidth = readInt();
        dwHeight = readInt();
        dwScale = readInt();
        dwRate = readInt();
        dwStart = readInt();
        dwLength = readInt();

        if (verbose) {
            IJ.log("AVI HEADER (avih):"+timeString());
            IJ.log("      dwMicroSecPerFrame=" + dwMicroSecPerFrame);
            IJ.log("      dwMaxBytesPerSec=" + dwMaxBytesPerSec);
            IJ.log("      dwReserved1=" + dwReserved1);
            IJ.log("      dwFlags=" + dwFlags);
            IJ.log("      dwTotalFrames=" + dwTotalFrames);
            IJ.log("      dwInitialFrames=" + dwInitialFrames);
            IJ.log("      dwStreams=" + dwStreams);
            IJ.log("      dwSuggestedBufferSize=" + dwSuggestedBufferSize);
            IJ.log("      dwWidth=" + dwWidth);
            IJ.log("      dwHeight=" + dwHeight);
            IJ.log("      dwScale=" + dwScale);
            IJ.log("      dwRate=" + dwRate);
            IJ.log("      dwStart=" + dwStart);
            IJ.log("      dwLength=" + dwLength);
        }
    }

    void readStreamHeader() throws Exception, IOException {
        fccStreamHandler = readInt();
        dwStreamFlags = readInt();
        dwStreamReserved1 = readInt();
        dwStreamInitialFrames = readInt();
        dwStreamScale = readInt();
        dwStreamRate = readInt();
        dwStreamStart = readInt();
        dwStreamLength = readInt();
        dwStreamSuggestedBufferSize = readInt();
        dwStreamQuality = readInt();
        dwStreamSampleSize = readInt();
        //rcFrame rectangle ignored
        if (verbose) {
            IJ.log("VIDEO STREAM HEADER (strh):");
            IJ.log("      fccStreamHandler='" + fourccString(fccStreamHandler)+"'");
            IJ.log("      dwStreamFlags=" + dwStreamFlags);
            IJ.log("      dwStreamReserved1=" + dwStreamReserved1);
            IJ.log("      dwStreamInitialFrames=" + dwStreamInitialFrames);
            IJ.log("      dwStreamScale=" + dwStreamScale);
            IJ.log("      dwStreamRate=" + dwStreamRate);
            IJ.log("      dwStreamStart=" + dwStreamStart);
            IJ.log("      dwStreamLength=" + dwStreamLength);
            IJ.log("      dwStreamSuggestedBufferSize=" + dwStreamSuggestedBufferSize);
            IJ.log("      dwStreamQuality=" + dwStreamQuality);
            IJ.log("      dwStreamSampleSize=" + dwStreamSampleSize);
        }
        if (dwStreamSampleSize > 1)
            throw new Exception("Video stream with "+dwStreamSampleSize+" (more than 1) frames/chunk not supported");
    }

    /**Read BitMapInfoHeader: starts with BitMapInfo, may contain palette
    */
    void readBitMapInfo(long endPosition) throws Exception, IOException {
        biSize = readInt();
        biWidth = readInt();
        biHeight = readInt();
        biPlanes = readShort();
        biBitCount = readShort();
        biCompression = readInt();
        biSizeImage = readInt();
        biXPelsPerMeter = readInt();
        biYPelsPerMeter = readInt();
        biClrUsed = readInt();
        biClrImportant = readInt();
        if (verbose) {
            IJ.log("      biSize=" + biSize);
            IJ.log("      biWidth=" + biWidth);
            IJ.log("      biHeight=" + biHeight);
            IJ.log("      biPlanes=" + biPlanes);
            IJ.log("      biBitCount=" + biBitCount);
            IJ.log("      biCompression=0x" + Integer.toHexString(biCompression)+" '"+fourccString(biCompression)+"'");
            IJ.log("      biSizeImage=" + biSizeImage);
            IJ.log("      biXPelsPerMeter=" + biXPelsPerMeter);
            IJ.log("      biYPelsPerMeter=" + biYPelsPerMeter);
            IJ.log("      biClrUsed=" + biClrUsed);
            IJ.log("      biClrImportant=" + biClrImportant);
        }

        int allowedBitCount = 0;
        boolean readPalette = false;
        switch (biCompression) {
            case NO_COMPRESSION:
            case NO_COMPRESSION_RGB:
            case NO_COMPRESSION_RAW:
                dataCompression = NO_COMPRESSION;
                dataTopDown = biHeight<0;   //RGB mode is usually bottom-up, negative height signals top-down
                allowedBitCount = 8 | BITMASK24 | 32; //we don't support 1, 2 and 4 byte data
                readPalette = biBitCount <= 8;
                break;
            case NO_COMPRESSION_Y8:
            case NO_COMPRESSION_GREY:
            case NO_COMPRESSION_Y800:
                dataTopDown = true;
                dataCompression = NO_COMPRESSION;
                allowedBitCount = 8;
                break;
            case NO_COMPRESSION_Y16:
                dataCompression = NO_COMPRESSION;
                allowedBitCount = 16;
                break;
            case AYUV_COMPRESSION:
                dataCompression = AYUV_COMPRESSION;
                allowedBitCount = 32;
                break;
            case UYVY_COMPRESSION:
            case UYNV_COMPRESSION:
                dataTopDown = true;
            case CYUV_COMPRESSION:  //same, not top-down
            case V422_COMPRESSION:
                dataCompression = UYVY_COMPRESSION;
                allowedBitCount = 16;
                break;
            case YUY2_COMPRESSION:
            case YUNV_COMPRESSION:
            case YUYV_COMPRESSION:
                dataTopDown = true;
                dataCompression = YUY2_COMPRESSION;
                allowedBitCount = 16;
                break;
            case YVYU_COMPRESSION:
                dataTopDown = true;
                dataCompression = YVYU_COMPRESSION;
                allowedBitCount = 16;
                break;
            default:
                throw new Exception("Unsupported compression: '"+fourccString(biCompression)+"' ("
                        + Integer.toHexString(biCompression) +")");
        }

        int bitCountTest = biBitCount==24 ? BITMASK24 : biBitCount;  //convert "24" to a flag
        if ((bitCountTest & allowedBitCount) == 0)
            throw new Exception("Unsupported: "+biBitCount+" bits/pixel for compression '"+
                    fourccString(biCompression)+"'");

        if (biHeight < 0)       //negative height was for top-down data in RGB mode
            biHeight = -biHeight;

        // Scan line is padded with zeroes to be a multiple of four bytes
        scanLineSize = ((biWidth * biBitCount + 31) / 32) * 4;

        // a value of biClrUsed=0 means we determine this based on the bits per pixel
        if (readPalette && biClrUsed==0)
            biClrUsed = 1 << biBitCount;

        if (verbose) {
            IJ.log("      > data compression=0x" + Integer.toHexString(dataCompression)+" '"+fourccString(dataCompression)+"'");
            IJ.log("      > palette colors=" + biClrUsed);
            IJ.log("      > scan line size=" + scanLineSize);
            IJ.log("      > data top down=" + dataTopDown);
        }

        //read color palette
        if (readPalette) {
            long spaceForPalette  = endPosition-raFile.getFilePointer();
            if (verbose)
                IJ.log("      Reading "+biClrUsed+" Palette colors: " + posSizeString(spaceForPalette));
            if (spaceForPalette < biClrUsed*4)
                throw new Exception("Not enough data ("+spaceForPalette+") for palette of size "+(biClrUsed*4));
            byte[]  pr    = new byte[biClrUsed];
            byte[]  pg    = new byte[biClrUsed];
            byte[]  pb    = new byte[biClrUsed];
            for (int i = 0; i < biClrUsed; i++) {
                pb[i] = raFile.readByte();
                pg[i] = raFile.readByte();
                pr[i] = raFile.readByte();
                raFile.readByte();
            }
            cm = new IndexColorModel(biBitCount, biClrUsed, pr, pg, pb);
        }
    }

    /**Read from the 'movi' chunk. Skips audio ('..wb', etc.), 'LIST' 'rec' etc, only reads '..db' or '..dc'*/
    void readMovieData(long endPosition) throws Exception, IOException {
        if (verbose)
            IJ.log("MOVIE DATA "+posSizeString(endPosition-raFile.getFilePointer())+timeString());
        //prepare for reading
        int type0xdb = FOURCC_00db + (streamNumber<<8);  //'01db' for stream 1, etc. (inverse byte order!)
        int type0xdc = FOURCC_00dc + (streamNumber<<8);  //'01dc' for stream 1, etc.
        if (verbose)
            IJ.log("Searching for stream "+streamNumber+": '"+
                    fourccString(type0xdb)+"' or '"+fourccString(type0xdb)+"' chunks");
        int lastFrameToRead = Integer.MAX_VALUE;
        if (lastFrameNumber > 0)            //last frame number to read: from Dialog
            lastFrameToRead = lastFrameNumber;
        if (lastFrameNumber < 0 && dwTotalFrames > 0) //negative means "end frame minus ..."
            lastFrameToRead = dwTotalFrames-lastFrameNumber;
        if (isVirtual)
            frameInfos = new Vector(100);    //holds frame positions in file (for non-constant frame sizes, should hold long[] with pos and size)
        else
            stack = new ImageStack(dwWidth, biHeight);
        int framesFound = 0;
        int frameNumber = 1;
        while (true) {                          //loop over all chunks
            int type = readType(endPosition);
            if (type==0) break;                 //end of 'movi' reached?
            long size = readInt() & SIZE_MASK;
            long pos = raFile.getFilePointer();
            long nextPos = pos + size;
            if (type==type0xdb || type==type0xdc) {
                updateProgress();
                if (verbose)
                    IJ.log("movie data '"+fourccString(type)+"' "+posSizeString(size)+timeString());
                if (frameNumber >= firstFrameNumber) {
                    if (isVirtual)
                        frameInfos.add(new long[]{pos, size, frameNumber*dwMicroSecPerFrame});
                    else {
                        Object pixels = readFrame(pos, (int)size); //read the frame
                        String label = frameLabel(frameNumber*dwMicroSecPerFrame);
                        stack.addSlice(label, pixels);
                    }
                }
                frameNumber++;
                if (frameNumber>lastFrameToRead) break;
            } else if (verbose)
                IJ.log("skipped '"+fourccString(type)+"' "+posSizeString(size));
            if (nextPos > endPosition) break;
            raFile.seek(nextPos);
        }
    }

    /** read a frame at a given position in the file, returns pixels array */
    private Object readFrame (long filePos, int size) throws Exception, IOException {
        raFile.seek(filePos);
        if (size < scanLineSize*biHeight)   //we have lines of equal length (very simple compression formats only)
            throw new Exception("Data chunk size "+size+" too short ("+(scanLineSize*biHeight)+" required)");
        byte[] rawData = new byte[size];
        int  n  = raFile.read(rawData, 0, size);
        if (n < rawData.length)
            throw new Exception("Frame ended prematurely after " + n + " bytes");

        boolean topDown = flipVertical ? !dataTopDown : dataTopDown;
        Object pixels = null;
        byte[] bPixels = null;
        int[] cPixels = null;
        if (biBitCount <=8 || convertToGray) {
            bPixels = new byte[dwWidth * biHeight];
            pixels = bPixels;
        } else {
            cPixels = new int[dwWidth * biHeight];
            pixels = cPixels;
        }
        int  offset     = topDown ? 0 : (biHeight-1)*dwWidth;
        int  rawOffset  = 0;
        for (int i = biHeight - 1; i >= 0; i--) {  //for all lines
            if (biBitCount <=8)
                unpack8bit(rawData, rawOffset, bPixels, offset, dwWidth);
            else if (convertToGray)
                unpackGray(rawData, rawOffset, bPixels, offset, dwWidth);
            else
                unpack(rawData, rawOffset, cPixels, offset, dwWidth);
            rawOffset += scanLineSize;
            offset += topDown ? dwWidth : -dwWidth;
        }
        return pixels;
    }

    /** For one line: copy byte data into the byte array for creating a ByteProcessor */
    void unpack8bit(byte[] rawData, int rawOffset, byte[] pixels, int byteOffset, int w) {
        for (int i = 0; i < w; i++)
            pixels[byteOffset + i] = rawData[rawOffset + i];
    }

    /** For one line: Unpack and convert YUV or RGB video data to grayscale (byte array for ByteProcessor) */
    void unpackGray(byte[] rawData, int rawOffset, byte[] pixels, int byteOffset, int w) {
        int  j     = byteOffset;
        int  k     = rawOffset;
        if (dataCompression == 0) {
            for (int i = 0; i < w; i++) {
                int  b0  = (((int) (rawData[k++])) & 0xff);
                int  b1  = (((int) (rawData[k++])) & 0xff);
                int  b2  = (((int) (rawData[k++])) & 0xff);
                if (biBitCount==32) k++; // ignore 4th byte (alpha value) 
                pixels[j++] = (byte)((b0*934 + b1*4809 + b2*2449 + 4096)>>13); //0.299*R+0.587*G+0.114*B
            }
        } else {
            if (dataCompression==UYVY_COMPRESSION || dataCompression==AYUV_COMPRESSION)
                k++; //skip first byte in these formats (chroma)
            int step = dataCompression==AYUV_COMPRESSION ? 4 : 2;
            for (int i = 0; i < w; i++) {
                pixels[j++] = rawData[k];   //Non-standard: no scaling from 16-235 to 0-255 here
                k+=step;
            }
        }
    }

    /** For one line: Read YUV, RGB or RGB+alpha data and writes RGB int array for ColorProcessor */
    void unpack(byte[] rawData, int rawOffset, int[] pixels, int intOffset, int w) {
        int  j     = intOffset;
        int  k     = rawOffset;
        switch (dataCompression) {
            case NO_COMPRESSION:
                for (int i = 0; i < w; i++) {
                    int  b0  = (((int) (rawData[k++])) & 0xff);
                    int  b1  = (((int) (rawData[k++])) & 0xff) << 8;
                    int  b2  = (((int) (rawData[k++])) & 0xff) << 16;
                    if (biBitCount==32) k++; // ignore 4th byte (alpha value) 
                    pixels[j++] = 0xff000000 | b0 | b1 | b2;
                }
                break;
            case YUY2_COMPRESSION:
                for (int i = 0; i < w/2; i++) {
                    int y0 = rawData[k++] & 0xff;
                    int u  = rawData[k++] ^ 0xffffff80; //converts byte range 0...ff to -128 ... 127
                    int y1 = rawData[k++] & 0xff;
                    int v  = rawData[k++] ^ 0xffffff80;
                    writeRGBfromYUV(y0, u, v, pixels, j++);
                    writeRGBfromYUV(y1, u, v, pixels, j++);
                }
                break;
            case UYVY_COMPRESSION:
                for (int i = 0; i < w/2; i++) {
                    int u  = rawData[k++] ^ 0xffffff80;
                    int y0 = rawData[k++] & 0xff;
                    int v  = rawData[k++] ^ 0xffffff80;
                    int y1 = rawData[k++] & 0xff;
                    writeRGBfromYUV(y0, u, v, pixels, j++);
                    writeRGBfromYUV(y1, u, v, pixels, j++);
                }
                break;
            case YVYU_COMPRESSION:
                for (int i = 0; i < w/2; i++) {
                    int y0 = rawData[k++] & 0xff;
                    int v  = rawData[k++] ^ 0xffffff80;
                    int y1 = rawData[k++] & 0xff;
                    int u  = rawData[k++] ^ 0xffffff80;
                    writeRGBfromYUV(y0, u, v, pixels, j++);
                    writeRGBfromYUV(y1, u, v, pixels, j++);
                }
                break;
            case AYUV_COMPRESSION:
                for (int i = 0; i < w; i++) {
                    k++;    //ignore alpha channel
                    int y  = rawData[k++] & 0xff;
                    int v  = rawData[k++] ^ 0xffffff80;
                    int u  = rawData[k++] ^ 0xffffff80;
                    writeRGBfromYUV(y, u, v, pixels, j++);
                }
                break;
                
        }
    }

    /** Write an intData RGB value converted from YUV,
     *  The y range between 16 and 235 becomes 0...255
     *  u, v should be between -112 and +112
     */
    void writeRGBfromYUV(int y, int u, int v, int[]pixels, int intArrayIndex) {
        //int r = (int)(1.164*(y-16)+1.596*v+0.5);
        //int g = (int)(1.164*(y-16)-0.391*u-0.813*v+0.5);
        //int b = (int)(1.164*(y-16)+2.018*u+0.5);
        int r = (9535*y + 13074*v -148464) >> 13;
        int g = (9535*y - 6660*v - 3203*u -148464) >> 13;
        int b = (9535*y + 16531*u -148464) >> 13;
        if (r>255) r=255; if (r<0) r=0;
        if (g>255) g=255; if (g<0) g=0;
        if (b>255) b=255; if (b<0) b=0;
        pixels[intArrayIndex] = 0xff000000 | (r<<16) | (g<<8) | b;
    }

    /** Read 4-byte int with Intel (little-endian) byte order
     * (note: RandomAccessFile.readInt has other byte order than AVI) */
    int readInt() throws IOException {
        int  result = 0;
        for (int shiftBy = 0; shiftBy < 32; shiftBy += 8)
            result |= (raFile.readByte() & 0xff) << shiftBy;
        return result;
    }

    /** Read 2-byte short with Intel (little-endian) byte order
     * (note: RandomAccessFile.readShort has other byte order than AVI) */
    short readShort() throws IOException {
        int  low   = raFile.readByte() & 0xff;
        int  high  = raFile.readByte() & 0xff;
        return (short) (high << 8 | low);
    }

    /** Read type of next chunk that is not JUNK.
     *  Returns type (or 0 if no non-JUNK chunk until endPosition) */
    private int readType(long endPosition) throws IOException {
        while (true) {
            long pos = raFile.getFilePointer();
            if (pos%paddingGranularity!=0) {
                pos = (pos/paddingGranularity+1)*paddingGranularity;
                raFile.seek(pos);    //pad to even address
            }
            if (pos >= endPosition) return 0;
            int type = readInt();
            if (type != FOURCC_JUNK)
                return type;
            long size = readInt()&SIZE_MASK;
            if (verbose)
                IJ.log("Skip JUNK: "+posSizeString(size));
            raFile.seek(raFile.getFilePointer()+size);  //skip junk
        }
    }

    private String frameLabel(long timeMicroSec) {
        return IJ.d2s(timeMicroSec/1.e6)+" s";
    }

    private String posSizeString(long size) throws IOException {
        return posSizeString(raFile.getFilePointer(), size);
    }

    private String posSizeString(long pos, long size) throws IOException {
        return "0x"+Long.toHexString(pos)+"-0x"+Long.toHexString(pos+size-1)+" ("+size+" Bytes)";
    }

    private String timeString() {
        return " (t="+(System.currentTimeMillis()-startTime)+" ms)";
    }

    /** returns a string of a four-cc code corresponding to an int (Intel byte order) */
    private String fourccString(int fourcc) {
        String s = "";
        for (int i=0; i<4; i++) {
            int c = fourcc&0xff;
            s += Character.toString((char)c);
            fourcc >>= 8;
        }
        return s;
    }

    private void showExceptionMessage (Exception e) {
        String  msg;
        if (e.getClass() == Exception.class)    //for "home-built" exceptions: message only
            msg = e.getMessage();
        else
            msg = e + "\n" + e.getStackTrace()[0]+"\n"+e.getStackTrace()[1];
        IJ.showMessage("AVI Reader", "An error occurred reading the file.\n \n" + msg);
    }

    void updateProgress() throws IOException {
        IJ.showProgress((double) raFile.getFilePointer() / fileSize);
    }

}
