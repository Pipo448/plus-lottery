// Enterfas sèvis enprimant SUNMI a ("Woyou" sèvis sistèm).
// Estrikti/non fonksyon sa yo estab depi plizyè ane nan SDK piblik SUNMI a.
// Si Android Studio siyale yon erè "method not found" pandan konpilasyon,
// konpare fichye sa a ak dènye vèsyon ofisyèl la sou developer.sunmi.com.
package woyou.aidlservice.jiuiv5;

import android.graphics.Bitmap;
import woyou.aidlservice.jiuiv5.ICallback;

interface IWoyouService {

    void printerInit(ICallback callback);
    void printerSelfChecking(ICallback callback);
    String getPrinterSerialNo();
    String getPrinterVersion();
    String getPrinterModal();
    String getServiceVersion();
    int updatePrinterState();
    int getPrinterState();

    void setPrinterStyle(int key, int value);
    void sendRAWData(in byte[] data, ICallback callback);

    void setAlignment(int alignment, ICallback callback);
    void setFontName(String typeface, ICallback callback);
    void setFontSize(float fontsize, ICallback callback);

    void printText(String text, ICallback callback);
    void printTextWithFont(String text, String typeface, float fontsize, ICallback callback);
    void printOriginalText(String text, ICallback callback);

    void printColumnsText(in String[] colsTextArr, in int[] colsWidthArr, in int[] colsAlign, ICallback callback);

    void printBitmap(in Bitmap bitmap, ICallback callback);
    void printBarCode(String data, int symbology, int height, int width, int textPosition, ICallback callback);
    void printQRCode(String data, int modulesize, int errorlevel, ICallback callback);

    void lineWrap(int n, ICallback callback);
    void cutpaper(ICallback callback);

    void sendRAWData2(String base64Data, ICallback callback);
    void setPrinterInit(ICallback callback);
}
