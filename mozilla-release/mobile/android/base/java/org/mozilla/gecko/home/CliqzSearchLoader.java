package org.mozilla.gecko.home;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

class CliqzSearchLoader {
    private static final String LOGTAG = "CliqzSearchLoader";
    private static final String KEY_SEARCH_TERM = "search_term";

    public static Loader<ArrayList<Bundle>> createInstance(Context context, Bundle args) {
        final String searchTerm = args != null ? args.getString(KEY_SEARCH_TERM, ""): "";
        final CliqzLoader loader = new CliqzLoader(context, searchTerm);
        loader.forceLoad();
        return loader;
    }

    public static void init(LoaderManager manager, int loaderId,
                            LoaderCallbacks<ArrayList<Bundle>> callbacks, String searchTerm) {
        final Bundle args = createArgs(searchTerm);
        manager.initLoader(loaderId, args, callbacks);
    }

    public static void restart(LoaderManager manager, int loaderId,
                               LoaderCallbacks<ArrayList<Bundle>> callbacks, String searchTerm) {
        manager.destroyLoader(loaderId);
        final Bundle args = createArgs(searchTerm);
        manager.restartLoader(loaderId, args, callbacks);
    }

    private static Bundle createArgs(String searchTerm) {
        final Bundle args = new Bundle();
        args.putString(KEY_SEARCH_TERM, searchTerm);
        return args;
    }

    private static class CliqzLoader extends AsyncTaskLoader<ArrayList<Bundle>> {
        private static final String SEARCH_FORMAT = "https://api.cliqz.com/api/v2/results?q=%s";
        private final String searchTerm;

        public CliqzLoader(Context context, String searchTerm) {
            super(context);
            this.searchTerm = searchTerm;
        }

        @Override
        public ArrayList<Bundle> loadInBackground() {
            final URL url = createUrl(searchTerm);
            final HttpsURLConnection connection = connectTo(url);
            if (connection == null) {
                return new ArrayList<>();
            }

            try {
                final int response = connection.getResponseCode();
                if (response != 200) {
                    Log.e(LOGTAG, "Wrong response from " + url.toString() + ": " + response);
                    return new ArrayList<>();
                }

                final String jsonStr = readAndClose(connection.getInputStream());
                return CliqzSearch.parseBackendResults(jsonStr);
            } catch (IOException e) {
                Log.e(LOGTAG, "Can't get a valid response from " + url.toString());
                return new ArrayList<>();
            } finally {
                // Cached anyway
                connection.disconnect();
            }
        }

        private static String readAndClose(InputStream inputStream) {
            final StringBuilder builder = new StringBuilder();
            final byte[] buffer = new byte[1024];

            try {
                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    final String str = new String(buffer, 0, read, "UTF-8");
                    builder.append(str);
                }
            } catch (IOException e) {
                Log.e(LOGTAG, "Error reading search response content");
                // Empty the buffer, it will contain nothing interesting
                builder.delete(0, builder.length());
            }

            try {
                inputStream.close();
            } catch (IOException e) {
                Log.w(LOGTAG, "Can't close the connection InputStream");
            }

            return builder.toString();
        }

        private static HttpsURLConnection connectTo(URL url) {
            if (url == null) {
                return null;
            }

            try {
                return (HttpsURLConnection) url.openConnection();
            } catch (IOException e) {
                Log.e(LOGTAG, "Can't open connection to " + url.toString());
                return null;
            }
        }

        private static URL createUrl(String searchTerm) {
            if (searchTerm == null || searchTerm.isEmpty()) {
                return null;
            }
            final String queryUrl = String.format(Locale.US, SEARCH_FORMAT, Uri.encode(searchTerm));
            try {
                return new URL(queryUrl);
            } catch (MalformedURLException e) {
                Log.e(LOGTAG, "Can't generate endpoint url for the query: " + searchTerm);
                return null;
            }
        }
    }
}
