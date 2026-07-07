// Enterfas kolback jeneral SUNMI sèvis enprimant la itilize.
// Estrikti sa a estab depi plizyè ane nan SDK SUNMI a.
package woyou.aidlservice.jiuiv5;

interface ICallback {
    void onRunResult(boolean isSuccess);
    void onReturnString(String result);
    void onRaiseException(int code, String msg);
    void onPrintResult(int code, String msg);
}
