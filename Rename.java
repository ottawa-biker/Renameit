import java.io.*;
import java.util.*;
import java.text.*;

import static java.util.Collections.singletonMap;
import java.lang.reflect.Method;
import com.sun.jna.FunctionMapper;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

/**
 * Renames AVI, MP4, and MOV files according to last modified date and time,
 * resolution, and frame rate.
 *
 * @author Ray
 */
public class Rename {

    static final SimpleDateFormat sdfFull = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
    static final SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");

    public static void main(String[] args) {
        new Rename(args);
    }

    public Rename(String[] args) {

        MediaInfo mediaInfo = new MediaInfo();

        File file;
        File newFile;
        File currentDirectory = new File(".");

        FileFilter mediaFileFilter = new FileFilter() {

            @Override
            public boolean accept(File file) {
                String name = file.getName().toUpperCase();

                return file.isFile() && file.canRead() && file.canWrite() &&
                        (name.endsWith(".AVI") || name.endsWith(".MP4") || name.endsWith(".MOV"));
            }
        };

        // List all supported media files in the current working directory
        File[] fileList = currentDirectory.listFiles(mediaFileFilter);

        if (fileList.length == 0) {
            msg("No media files to rename");
            System.exit(0);
        }

        File[] newFileList = new File[fileList.length];
        long[] lastModifiedList = new long[fileList.length];

        long currentDate = System.currentTimeMillis();
        long minDate = new GregorianCalendar(2000, 0, 1).getTimeInMillis();
        long maxDate = currentDate;
        long lastModifiedDateTime;

        String newFileNamePrefix = "";
        String fileName;
        String fileExtension;
        String newFileName;
        String reply;

        int lastPos;
        int i;

        /*
         * If the user provided 1 or more arguments, the first argument is a prefix to
         * be added to the beginning of all renamed files.
         */
        if (args.length >= 1 && args[0].length() > 0) {
            newFileNamePrefix = args[0] + "_";
        }

        /*
         * If the user provided a 2nd argument, then this will be used as a minimum date
         * when renaming the files. If a media file has a last modified date preceding
         * the min date, then the min date will be used instead.
         */
        if (args.length >= 2) {
            try {
                minDate = sdfDate.parse(args[1]).getTime();
            } catch (ParseException e) {
                msg("Second argument (minDate) must be formatted 9999-12-31");
                System.exit(0);
            }
        }

        /*
         * If the user provided a 3rd argument, then this will be used as a maximum date
         * when renaming the files. If a media file has a last modified date after the
         * max date, then the max date will be used instead.
         */
        if (args.length == 3) {
            try {
                maxDate = sdfDate.parse(args[2]).getTime();
            } catch (ParseException e) {
                msg("Third argument (maxDate) must be formatted 9999-12-31");
                System.exit(0);
            }
        }

        msg("");
        msg("The following files will be renamed:");
        msg("");
        for (i = 0; i < fileList.length; i++) {

            file = fileList[i];
            fileName = file.getName();

            mediaInfo.open(fileName);
            String resolution = mediaInfo.get(MediaInfo.StreamKind.Video, 0, "Height").trim();
            String frameRate = mediaInfo.get(MediaInfo.StreamKind.Video, 0, "FrameRate").trim();
            mediaInfo.close();

            // Add "p" after the resolution. Example: " 1080p "
            if (!resolution.isEmpty()) {
                resolution = " " + resolution + "p";
            }

            // Strip trailing zeros from frame rate
            if (!frameRate.isEmpty()) {
                frameRate = frameRate.trim();
                while (frameRate.endsWith("0")) {
                    frameRate = frameRate.substring(0, frameRate.length() - 1);
                }
                // Add space before and "fps" after the frame rate
                frameRate = " " + frameRate + "fps";
            }

            lastPos = fileName.lastIndexOf(".");
            fileExtension = fileName.substring(lastPos);

            // Determine the file date and time
            lastModifiedDateTime = file.lastModified();
            lastModifiedDateTime = determineDateTime(lastModifiedDateTime, minDate, maxDate);

            /*
             * The new file name format will be:
             * "prefix_9999-12-31 24.59.59 1080p.fileExtension"
             */
            newFileName = newFileNamePrefix + sdfFull.format(lastModifiedDateTime)
                    + resolution + frameRate + fileExtension;

            if (!fileName.equals(newFileName)) {
                newFile = new File(newFileName);
                if (!newFile.exists()) {
                    newFileList[i] = newFile;
                    lastModifiedList[i] = lastModifiedDateTime;
                    msg("    " + pad(fileName, 60) + " --> " + newFile.getAbsolutePath());
                }
            }
        }

        msg("");
        System.out.print("Proceed? (Y/N): ");
        Scanner scanner = new Scanner(System.in);
        reply = scanner.next().toUpperCase();
        if (reply.equals("Y")) {

            // If user types "Y", then rename the files
            for (i = 0; i < fileList.length; i++) {
                if (newFileList[i] != null) {
                    file = fileList[i];
                    newFile = newFileList[i];

                    if (file.renameTo(newFile)) {
                        lastModifiedDateTime = lastModifiedList[i];
                        file.setLastModified(lastModifiedDateTime);
                    } else {
                        msg("Could not rename " + file.getName());
                    }
                }

            }
        }
        scanner.close();

    }

    /**
     * Right-pads a string the specified number of spaces.
     */
    public static String pad(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }

    /**
     * Prints a message to the system console.
     */
    public static void msg(Object o) {
        System.out.println(o);
    }

    /**
     * Substitutes the date portion of a timestamp if it comes before the given min
     * date or after the given max date.
     *
     * @param dateTime the date and time to check
     * @param minDate the minimum date allowed
     * @param maxDate the maximum date allowed
     * @return a new dateTime within the min and max dates
     */
    public static long determineDateTime(long dateTime, long minDate, long maxDate) {

        GregorianCalendar gc1, gc2;

        if (dateTime < minDate) {

            gc1 = new GregorianCalendar();
            gc1.setTimeInMillis(minDate);
            gc2 = new GregorianCalendar();
            gc2.setTimeInMillis(dateTime);
            gc1.set(GregorianCalendar.HOUR_OF_DAY, gc2.get(GregorianCalendar.HOUR_OF_DAY));
            gc1.set(GregorianCalendar.MINUTE, gc2.get(GregorianCalendar.MINUTE));
            gc1.set(GregorianCalendar.SECOND, gc2.get(GregorianCalendar.SECOND));
            dateTime = gc1.getTimeInMillis();
        }

        if (dateTime > maxDate) {

            gc1 = new GregorianCalendar();
            gc1.setTimeInMillis(maxDate);
            gc2 = new GregorianCalendar();
            gc2.setTimeInMillis(dateTime);
            gc1.set(GregorianCalendar.HOUR_OF_DAY, gc2.get(GregorianCalendar.HOUR_OF_DAY));
            gc1.set(GregorianCalendar.MINUTE, gc2.get(GregorianCalendar.MINUTE));
            gc1.set(GregorianCalendar.SECOND, gc2.get(GregorianCalendar.SECOND));
            dateTime = gc1.getTimeInMillis();
        }
        return dateTime;
    }
}

class MediaInfo {
    static {
        // libmediainfo for linux depends on libzen
        try {
            // We need to load dependencies first, because we know where our native libs are
            // (e.g. Java Web Start Cache).
            // If we do not, the system will look for dependencies, but only in the library
            // path.
            String os = System.getProperty("os.name");
            if (os != null && !os.toLowerCase().startsWith("windows") && !os.toLowerCase().startsWith("mac"))
                NativeLibrary.getInstance("zen");
        } catch (LinkageError e) {
            // Logger.getLogger(MediaInfo.class.getName()).warning("Failed to preload
            // libzen");
        }
    }

    // Internal stuff
    interface MediaInfoDLL_Internal extends Library {

        MediaInfoDLL_Internal INSTANCE = (MediaInfoDLL_Internal) Native.loadLibrary("mediainfo",
                MediaInfoDLL_Internal.class,
                singletonMap(OPTION_FUNCTION_MAPPER, new FunctionMapper() {

                    @Override
                    public String getFunctionName(NativeLibrary lib, Method method) {
                        // MediaInfo_New(), MediaInfo_Open() ...
                        return "MediaInfo_" + method.getName();
                    }
                }));

        // Constructor/Destructor
        Pointer New();

        void Delete(Pointer Handle);

        // File
        int Open(Pointer Handle, WString file);

        void Close(Pointer Handle);

        // Infos
        WString Inform(Pointer Handle, int Reserved);

        WString Get(Pointer Handle, int StreamKind, int StreamNumber, WString parameter, int infoKind, int searchKind);

        WString GetI(Pointer Handle, int StreamKind, int StreamNumber, int parameterIndex, int infoKind);

        int Count_Get(Pointer Handle, int StreamKind, int StreamNumber);

        // Options
        WString Option(Pointer Handle, WString option, WString value);
    }

    private Pointer Handle;

    public enum StreamKind {
        General,
        Video,
        Audio,
        Text,
        Chapters,
        Image,
        Menu;
    }

    // Enums
    public enum InfoKind {
        Name,
        Text,
        Measure,
        Options,
        Name_Text,
        Measure_Text,
        Info,
        HowTo,
        Domain;
    }

    // Constructor/Destructor
    public MediaInfo() {
        Handle = MediaInfoDLL_Internal.INSTANCE.New();
    }

    public void dispose() {
        if (Handle == null)
            throw new IllegalStateException();

        MediaInfoDLL_Internal.INSTANCE.Delete(Handle);
        Handle = null;
    }

    @Override
    protected void finalize() throws Throwable {
        if (Handle != null)
            dispose();
    }

    // File
    /**
     * Open a file and collect information about it (technical information and
     * tags).
     *
     * @param file full name of the file to open
     * @return 1 if file was opened, 0 if file was not not opened
     */
    public int open(String File_Name) {
        return MediaInfoDLL_Internal.INSTANCE.Open(Handle, new WString(File_Name));
    }

    /**
     * Close a file opened before with Open().
     *
     */
    public void close() {
        MediaInfoDLL_Internal.INSTANCE.Close(Handle);
    }

    // Information
    /**
     * Get all details about a file.
     *
     * @return All details about a file in one string
     */
    public String inform() {
        return MediaInfoDLL_Internal.INSTANCE.Inform(Handle, 0).toString();
    }

    /**
     * Get a piece of information about a file (parameter is a string).
     *
     * @param StreamKind Kind of Stream (general, video, audio...)
     * @param StreamNumber Stream number in Kind of Stream (first, second...)
     * @param parameter Parameter you are looking for in the Stream (Codec, width,
     * bitrate...), in string format ("Codec", "Width"...)
     * @return a string about information you search, an empty string if there is a
     * problem
     */
    public String get(StreamKind StreamKind, int StreamNumber, String parameter) {
        return MediaInfoDLL_Internal.INSTANCE.Get(
                Handle, StreamKind.ordinal(), StreamNumber,
                new WString(parameter), InfoKind.Text.ordinal(),
                InfoKind.Name.ordinal()).toString();
    }
}
