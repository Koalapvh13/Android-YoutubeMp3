package com.github.florent37.youtubemp3downloader.repository;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;

import com.github.florent37.youtubemp3downloader.Video;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

/**
 * Created by florentchampigny on 17/03/2017.
 */

public class FileDownloaderRepositoryImpl implements FileDownloaderRepository {

    private final OkHttpClient okHttpClient;

    public FileDownloaderRepositoryImpl(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    public <V> ObservableTransformer<V, V> retryTransformer() {
        return new ObservableTransformer<V, V>() {

            @Override
            public ObservableSource<V> apply(Observable<V> upstream) {
                return upstream.retryWhen(new Function<Observable<Throwable>, ObservableSource<?>>() {
                    @Override
                    public ObservableSource<?> apply(@NonNull Observable<Throwable> throwableObservable) throws Exception {
                        return Observable.just(1); //ton code ici
                    }
                });
            }
        };
    }

    @Override
    public Observable<Video> download(final Context context, Video video) {
        Observable.just(Arrays.asList("1", "2"))
                .compose(retryTransformer())
                .subscribe(new Consumer<List<String>>() {
                    @Override
                    public void accept(@NonNull List<String> result) throws Exception {

                    }
                });


        return Observable.create((ObservableOnSubscribe<File>) subscription -> {

            final Request request = new Request.Builder()
                    .url(video.getLink())
                    .build();

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    subscription.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String fileName = video.getTitle().replaceAll("[^a-zA-Z]+", "");
                    final File file = new File(Environment.getExternalStorageDirectory(), "/Download/" + fileName + ".mp3");

                    try {
                        file.createNewFile();

                        BufferedSink sink = Okio.buffer(Okio.sink(file));
                        sink.writeAll(response.body().source());
                        sink.close();
                    } catch (Exception e) {
                        subscription.onError(e);
                        throw e;
                    }

                    subscription.onNext(file);
                    subscription.onComplete();
                }
            });
        }).flatMap(new Function<File, ObservableSource<Video>>() {
            @Override
            public ObservableSource<Video> apply(@NonNull File file) throws Exception {
                return Observable.create(e -> MediaScannerConnection.scanFile(
                        context,
                        new String[]{file.getAbsolutePath()},
                        null,
                        (path, uri) -> {
                            e.onNext(video);
                            e.onComplete();
                        }));
            }
        });
    }
}
