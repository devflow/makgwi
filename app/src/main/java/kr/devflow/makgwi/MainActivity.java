package kr.devflow.makgwi;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.StringDef;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.FFmpegExecuteResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnCompleteConvert {

    private static final int OPEN_FILE = 20;
    private static final int PERMISSION_REQUEST_STORAGE = 123;
    private static final String FORMAT_128K = "%s_0.mp3";
    private static final String FORMAT_192K = "%s_1.mp3";
    private static final String FORMAT_320K = "%s_2.mp3";

    private FFmpeg ffmpeg;
    private Button btn_load;

    private Map<String, Boolean> converted_audios;
    private File target_file;

    private ProgressDialog progressDialog;

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(ffmpeg != null){
            ffmpeg.killRunningProcesses();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ffmpeg = FFmpeg.getInstance(this);
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {

                @Override
                public void onStart() {
                    Log.e("FFMPEG", "onStart!");
                }

                @Override
                public void onFailure() {
                    Log.e("FFMPEG", "onFailure!");
                }

                @Override
                public void onSuccess() {
                    Log.e("FFMPEG", "onSuccess!");
                }

                @Override
                public void onFinish() {
                    Log.e("FFMPEG", "onFinish!");
                }
            });
        } catch (FFmpegNotSupportedException e) {
            Toast.makeText(this, R.string.not_supported_arch, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btn_load = (Button)findViewById(R.id.btn_load);
        btn_load.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAndOpenFileChooser();
            }
        });

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(null);
        progressDialog.setCancelable(false);
    }


    private void checkAndOpenFileChooser(){
        if (Build.VERSION.SDK_INT > 22) {
            checkPermission();
            return;
        }

        openFileChooser();
    }

    private void openFileChooser(){
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, OPEN_FILE);
    }

    /**
     * Permission check.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermission() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Explain to the user why we need to write the permission.
                Toast.makeText(this, R.string.file_permission, Toast.LENGTH_SHORT).show();
            }

            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_STORAGE);
        } else {
            openFileChooser();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        checkPermission();
    }

    private Map<String, Boolean> getConvertedFiles(File file){
        Map<String, Boolean> files = new HashMap<>();
        files.put("128k", false);
        files.put("192k", false);
        files.put("320k", false);

        if(!file.exists()){
            return files;
        }

        File base_dir = getTempDir();

        if(base_dir == null){
            return files;
        }

        String md5 = MD5.calculateMD5(file);

        if(md5 == null){
            return files;
        }

        File temp_128k = new File(base_dir, getFormatFileName(md5, "128k"));
        File temp_192k = new File(base_dir, getFormatFileName(md5, "192k"));
        File temp_320k = new File(base_dir, getFormatFileName(md5, "320k"));

        files.put("128k", temp_128k.exists());
        files.put("192k", temp_192k.exists());
        files.put("320k", temp_320k.exists());

        return files;
    }

    public String getFormatFileName(String md5, String bitrate){
        if (bitrate.equals("128k")) {
            return String.format(MainActivity.FORMAT_128K, md5);
        } else if (bitrate.equals("192k")) {
            return String.format(MainActivity.FORMAT_192K, md5);
        } else {
            return String.format(MainActivity.FORMAT_320K, md5);
        }
    }

    public void convertAudio(File request, String bitrate, final OnCompleteConvert callback){
        try {
            File basedir = getTempDir();

            if(basedir == null){
                callback.failed();
                return;
            }

            String md5 = MD5.calculateMD5(request);

            if(md5 == null){
                callback.failed();
                return;
            }

            String file_name = getFormatFileName(md5, bitrate);

            File target = new File(basedir, file_name);

            ffmpeg.execute(new String[]{
                    "-i", request.getAbsolutePath(),
                    "-codec:a", "libmp3lame",
                    "-b:a", bitrate,
                    "-map_metadata", "-1",
                    target.getAbsolutePath()
            }, new ExecuteBinaryResponseHandler() {
                @Override
                public void onSuccess(String message) {
                    callback.success();
                }

                @Override
                public void onProgress(String message) {
                    callback.progress(message);
                }

                @Override
                public void onFailure(String message) {
                    Log.e("onfailed123133", message);
                    callback.failed();
                }

                @Override
                public void onStart() {
                    callback.start();
                }

                @Override
                public void onFinish() {
                    callback.complete();
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            e.printStackTrace();
            callback.failed();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OPEN_FILE && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            File temp_dir = getTempDir();
            if (data != null && temp_dir != null) {
                uri = data.getData();

                if(uri == null || ffmpeg == null){
                    Toast.makeText(this, R.string.raise_error, Toast.LENGTH_SHORT).show();
                    return;
                }

                String real_path_of_flac = PathUtils.getRealPath(this, uri);

                if(real_path_of_flac == null){
                    Toast.makeText(this, R.string.raise_error, Toast.LENGTH_SHORT).show();
                    return;
                }

                target_file = new File(real_path_of_flac);

                if(!target_file.exists()){
                    Toast.makeText(this, R.string.raise_error, Toast.LENGTH_SHORT).show();
                }

                preparePlaying();
            }
        }
    }

    public void preparePlaying(){
        converted_audios = getConvertedFiles(target_file);
        for (String bitrate : converted_audios.keySet()) {
            if(!converted_audios.get(bitrate)) {
                convertAudio(target_file, bitrate, this);
                return;
            }
        }

        Toast.makeText(this, "OK", Toast.LENGTH_LONG).show();
    }

    public File getTempDir(){
        File baseDir = Environment.getExternalStorageDirectory();
        File folder = new File(baseDir, ".makgwi");

        if (folder.exists()) {
            return folder;
        }
        if (folder.mkdirs()) {
            return folder;
        }

        return Environment.getExternalStorageDirectory();
    }

    public void cleanTempDir(){
    }

    @Override
    public void success() {
        Log.e("FFMPEG", "success");
        progressDialog.dismiss();
        preparePlaying();
    }

    @Override
    public void start() {
        int not_progressed = 0;

        for (String bitrate :
                converted_audios.keySet()) {
            not_progressed += (converted_audios.get(bitrate) ? 0 : 1);
        }

        progressDialog.setMessage("(" + (4 - not_progressed) + "/3) 파일을 변환중입니다. 수분 걸릴 수 있습니다.");
        progressDialog.show();
    }

    @Override
    public void progress(String message) {
    }

    @Override
    public void complete() {
    }

    @Override
    public void failed() {
        progressDialog.dismiss();
        Log.e("FFMPEG", "fail");
        Toast.makeText(this, R.string.raise_error, Toast.LENGTH_SHORT).show();
    }
}
