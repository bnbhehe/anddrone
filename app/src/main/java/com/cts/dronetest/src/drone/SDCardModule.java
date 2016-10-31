package com.cts.dronetest.src.drone;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;

import com.cts.dronetest.src.activities.BebopActivity;
import com.parrot.arsdk.ardatatransfer.ARDATATRANSFER_ERROR_ENUM;
import com.parrot.arsdk.ardatatransfer.ARDataTransferException;
import com.parrot.arsdk.ardatatransfer.ARDataTransferManager;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMedia;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloader;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloaderCompletionListener;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloaderProgressListener;
import com.parrot.arsdk.arutils.ARUtilsManager;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

public class SDCardModule {

    private static final String TAG = "SDCardModule";

    private static final String DRONE_MEDIA_FOLDER = "internal_000";
    private static final String MOBILE_MEDIA_FOLDER = "/ARSDKMedias/";
    public Activity activity;

    public interface Listener {
        /**
         * Called before medias will be downloaded
         * Called on a separate thread
         *
         * @param nbMedias the number of medias that will be downloaded
         */
        void onMatchingMediasFound(int nbMedias);

        /**
         * Called each time the progress of a download changes
         * Called on a separate thread
         *
         * @param mediaName the name of the media
         * @param progress  the progress of its download (from 0 to 100)
         */
        void onDownloadProgressed(String mediaName, int progress);

        /**
         * Called when a media download has ended
         * Called on a separate thread
         *
         * @param mediaName the name of the media
         */
        void onDownloadComplete(String mediaName);
    }

    private final List<Listener> mListeners;

    private ARDataTransferManager mDataTransferManager;
    private ARUtilsManager mFtpList;
    private ARUtilsManager mFtpQueue;

    private boolean mThreadIsRunning;
    private boolean mIsCancelled;

    private int mNbMediasToDownload;
    private int mCurrentDownloadIndex;

    public SDCardModule(@NonNull ARUtilsManager ftpListManager, @NonNull ARUtilsManager ftpQueueManager) {

        mThreadIsRunning = false;
        mListeners = new ArrayList<>();

        mFtpList = ftpListManager;
        mFtpQueue = ftpQueueManager;

        ARDATATRANSFER_ERROR_ENUM result = ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK;
        try {
            mDataTransferManager = new ARDataTransferManager();
        } catch (ARDataTransferException e) {
            Log.e(TAG, "Exception", e);
            result = ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_ERROR;
        }

        if (result == ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK) {
            // direct to external directory
            String externalDirectory = Environment.getExternalStorageDirectory().toString().concat(MOBILE_MEDIA_FOLDER);

            // if the directory doesn't exist, create it
            File f = new File(externalDirectory);
            if (!(f.exists() && f.isDirectory())) {
                boolean success = f.mkdir();
                if (!success) {
                    Log.e(TAG, "Failed to create the folder " + externalDirectory);
                }
            }
            try {
                mDataTransferManager.getARDataTransferMediasDownloader().createMediasDownloader(mFtpList, mFtpQueue, DRONE_MEDIA_FOLDER, externalDirectory);
            } catch (ARDataTransferException e) {
                Log.e(TAG, "Exception", e);
                result = e.getError();
            }
        }

        if (result != ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK) {
            // clean up here because an error happened
            mDataTransferManager.dispose();
            mDataTransferManager = null;
        }
    }

    //region Listener functions
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }
    //endregion Listener

    public void getFlightMedias(final String runId) {
        if (!mThreadIsRunning) {
            mThreadIsRunning = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ArrayList<ARDataTransferMedia> mediaList = getMediaList();

                    ArrayList<ARDataTransferMedia> mediasFromRun = null;
                    mNbMediasToDownload = 0;
                    if ((mediaList != null) && !mIsCancelled) {
                        mediasFromRun = getRunIdMatchingMedias(mediaList, runId);
                        mNbMediasToDownload = mediasFromRun.size();
                    }

                    notifyMatchingMediasFound(mNbMediasToDownload);

                    if ((mediasFromRun != null) && (mNbMediasToDownload != 0) && !mIsCancelled) {
                        downloadMedias(mediasFromRun);
                    }

                    mThreadIsRunning = false;
                    mIsCancelled = false;
                }
            }).start();
        }
    }

    public void getTodaysFlightMedias() {
        if (!mThreadIsRunning) {
            mThreadIsRunning = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ArrayList<ARDataTransferMedia> mediaList = getMediaList();

                    ArrayList<ARDataTransferMedia> mediasFromDate = null;
                    mNbMediasToDownload = 0;
                    if ((mediaList != null) && !mIsCancelled) {
                        GregorianCalendar today = new GregorianCalendar();
                        mediasFromDate = getDateMatchingMedias(mediaList, today);
                        mNbMediasToDownload = mediasFromDate.size();
                        Log.e(TAG, "Media to download :" + mNbMediasToDownload);
                    }

                    notifyMatchingMediasFound(mNbMediasToDownload);

                    if ((mediasFromDate != null) && (mNbMediasToDownload != 0) && !mIsCancelled) {
                        Log.e(TAG, "Downloading medias:" + mediasFromDate.size());
                        downloadMedias(mediasFromDate);
                    }

                    mThreadIsRunning = false;
                    mIsCancelled = false;
                }
            }).start();
        }
    }

    public void cancelGetFlightMedias() {
        if (mThreadIsRunning) {
            mIsCancelled = true;
            ARDataTransferMediasDownloader mediasDownloader = null;
            if (mDataTransferManager != null) {
                mediasDownloader = mDataTransferManager.getARDataTransferMediasDownloader();
            }

            if (mediasDownloader != null) {
                mediasDownloader.cancelQueueThread();
            }
        }
    }

    private ArrayList<ARDataTransferMedia> getMediaList() {
        ArrayList<ARDataTransferMedia> mediaList = null;

        ARDataTransferMediasDownloader mediasDownloader = null;
        if (mDataTransferManager != null) {
            mediasDownloader = mDataTransferManager.getARDataTransferMediasDownloader();
        }

        if (mediasDownloader != null) {
            try {
                int mediaListCount = mediasDownloader.getAvailableMediasSync(false);
                mediaList = new ArrayList<>(mediaListCount);
                for (int i = 0; ((i < mediaListCount) && !mIsCancelled); i++) {
                    ARDataTransferMedia currentMedia = mediasDownloader.getAvailableMediaAtIndex(i);
                    mediaList.add(currentMedia);
                }
            } catch (ARDataTransferException e) {
                Log.e(TAG, "Exception", e);
                mediaList = null;
            }
        }
        return mediaList;
    }

    private
    @NonNull
    ArrayList<ARDataTransferMedia> getRunIdMatchingMedias(
            ArrayList<ARDataTransferMedia> mediaList,
            String runId) {
        ArrayList<ARDataTransferMedia> matchingMedias = new ArrayList<>();
        for (ARDataTransferMedia media : mediaList) {
            if (media.getName().contains(runId)) {
                matchingMedias.add(media);
            }

            // exit if the async task is cancelled
            if (mIsCancelled) {
                break;
            }
        }

        return matchingMedias;
    }

    private boolean todaysMedia(Calendar media, Calendar match) {
        return (media.get(Calendar.DAY_OF_MONTH) == (match.get(Calendar.DAY_OF_MONTH))) &&
                (media.get(Calendar.MONTH) == (match.get(Calendar.MONTH))) &&
                (media.get(Calendar.YEAR) == (match.get(Calendar.YEAR)));
    }

    private ArrayList<ARDataTransferMedia> getDateMatchingMedias(ArrayList<ARDataTransferMedia> mediaList,
                                                                 GregorianCalendar matchingCal) {
        ArrayList<ARDataTransferMedia> matchingMedias = new ArrayList<>();
        Calendar mediaCal = new GregorianCalendar();
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss", Locale.getDefault());
        for (ARDataTransferMedia media : mediaList) {
            // convert date in string to calendar
            String dateStr = media.getDate();
            try {
                Date mediaDate = dateFormatter.parse(dateStr);
                mediaCal.setTime(mediaDate);
                Log.e(TAG, "Media date:" + mediaCal.YEAR + "-" + mediaCal.MONTH + "-"
                        + mediaCal.DAY_OF_MONTH + " " + mediaCal.HOUR_OF_DAY
                        + ":" + mediaCal.MINUTE + ":" + mediaCal.SECOND);
                // if the date are the same day
                if (todaysMedia(mediaCal, matchingCal) && media.getName().contains("jpg")) {
                    matchingMedias.add(media);
                }


            } catch (ParseException e) {
                Log.e(TAG, "Exception", e);
            }

            // exit if the async task is cancelled
            if (mIsCancelled) {
                break;
            }
        }

        return matchingMedias;
    }




    private void downloadMedias(@NonNull ArrayList<ARDataTransferMedia> matchingMedias) {
        mCurrentDownloadIndex = 1;

        ARDataTransferMediasDownloader mediasDownloader = null;
        if (mDataTransferManager != null) {
            mediasDownloader = mDataTransferManager.getARDataTransferMediasDownloader();
        }

        if (mediasDownloader != null) {
            for (ARDataTransferMedia media : matchingMedias) {
                try {
                    mediasDownloader.addMediaToQueue(media, mDLProgressListener, null, mDLCompletionListener, null);
                } catch (ARDataTransferException e) {
                    Log.e(TAG, "Exception", e);
                }

                // exit if the async task is cancelled
                if (mIsCancelled) {
                    break;
                }
            }

            if (!mIsCancelled) {
//                mFileTransferThread = new HandlerThread("FileTransferThread");
//                mFileTransferThread.start();
//                mFileTransferThreadHandler = new Handler(mFileTransferThread.getLooper());
                mediasDownloader.getDownloaderQueueRunnable().run();
                try {
                    ARDataTransferMedia media = mediasDownloader.getAvailableMediaAtIndex(0);
                    File f = new File(media.getFilePath());
                    Log.e(TAG, "File path: " + f.getAbsolutePath());
                } catch (ARDataTransferException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    //region notify listener block
    private void notifyMatchingMediasFound(int nbMedias) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onMatchingMediasFound(nbMedias);
        }
    }

    private void notifyDownloadProgressed(String mediaName, int progress) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onDownloadProgressed(mediaName, progress);
        }
    }

    private void notifyDownloadComplete(String mediaName) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onDownloadComplete(mediaName);
        }
    }
    //endregion notify listener block

    private final ARDataTransferMediasDownloaderProgressListener mDLProgressListener = new ARDataTransferMediasDownloaderProgressListener() {
        private int mLastProgressSent = -1;

        @Override
        public void didMediaProgress(Object arg, ARDataTransferMedia media, float percent) {
            final int progressInt = (int) Math.floor(percent);
            if (mLastProgressSent != progressInt) {
                mLastProgressSent = progressInt;
                notifyDownloadProgressed(media.getName(), progressInt);
            }
        }
    };

    private final ARDataTransferMediasDownloaderCompletionListener mDLCompletionListener = new ARDataTransferMediasDownloaderCompletionListener() {
        @Override
        public void didMediaComplete(Object arg, ARDataTransferMedia media, ARDATATRANSFER_ERROR_ENUM error) {
            Log.e(TAG, "Downloaded " + media.getName());
            notifyDownloadComplete(media.getName());
            File f = new File(media.getName());
            if (media.getName().contains(".jpg"))
                //addImageToGallery(f.getAbsolutePath(), activity.getApplicationContext() );
                // when all download are finished, stop the download runnable
                // in order to get out of the downloadMedias function
                mCurrentDownloadIndex++;
            if (mCurrentDownloadIndex > mNbMediasToDownload) {
                ARDataTransferMediasDownloader mediasDownloader = null;
                if (mDataTransferManager != null) {
                    mediasDownloader = mDataTransferManager.getARDataTransferMediasDownloader();
                }

                if (mediasDownloader != null) {
                    mediasDownloader.cancelQueueThread();
                }
            }
        }
    };

    public void addImageToGallery(final String filePath, final Context context) {

        ContentValues values = new ContentValues();

        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.DATA, filePath);

        context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }
}
