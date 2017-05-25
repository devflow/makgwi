package kr.devflow.makgwi;

/**
 * Created by nil on 2017-05-01.
 */

public interface OnCompleteConvert {
    void success();
    void start();
    void progress(String message);
    void complete();
    void failed();
}
