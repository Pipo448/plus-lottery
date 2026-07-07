/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: C:\Users\dasne\AppData\Local\Android\Sdk\build-tools\35.0.0\aidl.exe -pC:\Users\dasne\AppData\Local\Android\Sdk\platforms\android-34\framework.aidl -oC:\Users\dasne\Documents\sunmi-pos\app\build\generated\aidl_source_output_dir\debug\out -IC:\Users\dasne\Documents\sunmi-pos\app\src\main\aidl -IC:\Users\dasne\Documents\sunmi-pos\app\src\debug\aidl -IC:\Users\dasne\.gradle\caches\9.3.0\transforms\6aa83e80bd46fb1ea88754322e731f9e\workspace\transformed\core-1.13.1\aidl -IC:\Users\dasne\.gradle\caches\9.3.0\transforms\35182f06f0e0f9207d202ad1417d9317\workspace\transformed\versionedparcelable-1.1.1\aidl -dC:\Users\dasne\AppData\Local\Temp\aidl15992726321592640357.d C:\Users\dasne\Documents\sunmi-pos\app\src\main\aidl\woyou\aidlservice\jiuiv5\IWoyouService.aidl
 */
package woyou.aidlservice.jiuiv5;
public interface IWoyouService extends android.os.IInterface
{
  /** Default implementation for IWoyouService. */
  public static class Default implements woyou.aidlservice.jiuiv5.IWoyouService
  {
    @Override public void printerInit(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void printerSelfChecking(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public java.lang.String getPrinterSerialNo() throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String getPrinterVersion() throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String getPrinterModal() throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String getServiceVersion() throws android.os.RemoteException
    {
      return null;
    }
    @Override public int updatePrinterState() throws android.os.RemoteException
    {
      return 0;
    }
    @Override public int getPrinterState() throws android.os.RemoteException
    {
      return 0;
    }
    @Override public void setPrinterStyle(int key, int value) throws android.os.RemoteException
    {
    }
    @Override public void sendRAWData(byte[] data, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void setAlignment(int alignment, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void setFontName(java.lang.String typeface, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void setFontSize(float fontsize, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void printText(java.lang.String text, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void printTextWithFont(java.lang.String text, java.lang.String typeface, float fontsize, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void printOriginalText(java.lang.String text, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void printColumnsText(java.lang.String[] colsTextArr, int[] colsWidthArr, int[] colsAlign, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void printBitmap(android.graphics.Bitmap bitmap, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void printBarCode(java.lang.String data, int symbology, int height, int width, int textPosition, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void printQRCode(java.lang.String data, int modulesize, int errorlevel, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void lineWrap(int n, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void cutpaper(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void sendRAWData2(java.lang.String base64Data, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override public void setPrinterInit(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements woyou.aidlservice.jiuiv5.IWoyouService
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an woyou.aidlservice.jiuiv5.IWoyouService interface,
     * generating a proxy if needed.
     */
    public static woyou.aidlservice.jiuiv5.IWoyouService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof woyou.aidlservice.jiuiv5.IWoyouService))) {
        return ((woyou.aidlservice.jiuiv5.IWoyouService)iin);
      }
      return new woyou.aidlservice.jiuiv5.IWoyouService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_printerInit:
        {
          woyou.aidlservice.jiuiv5.ICallback _arg0;
          _arg0 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.printerInit(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_printerSelfChecking:
        {
          woyou.aidlservice.jiuiv5.ICallback _arg0;
          _arg0 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.printerSelfChecking(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getPrinterSerialNo:
        {
          java.lang.String _result = this.getPrinterSerialNo();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_getPrinterVersion:
        {
          java.lang.String _result = this.getPrinterVersion();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_getPrinterModal:
        {
          java.lang.String _result = this.getPrinterModal();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_getServiceVersion:
        {
          java.lang.String _result = this.getServiceVersion();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_updatePrinterState:
        {
          int _result = this.updatePrinterState();
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_getPrinterState:
        {
          int _result = this.getPrinterState();
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_setPrinterStyle:
        {
          int _arg0;
          _arg0 = data.readInt();
          int _arg1;
          _arg1 = data.readInt();
          this.setPrinterStyle(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_sendRAWData:
        {
          byte[] _arg0;
          _arg0 = data.createByteArray();
          woyou.aidlservice.jiuiv5.ICallback _arg1;
          _arg1 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.sendRAWData(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_setAlignment:
        {
          int _arg0;
          _arg0 = data.readInt();
          woyou.aidlservice.jiuiv5.ICallback _arg1;
          _arg1 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.setAlignment(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_setFontName:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          woyou.aidlservice.jiuiv5.ICallback _arg1;
          _arg1 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.setFontName(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_setFontSize:
        {
          float _arg0;
          _arg0 = data.readFloat();
          woyou.aidlservice.jiuiv5.ICallback _arg1;
          _arg1 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.setFontSize(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_printText:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          woyou.aidlservice.jiuiv5.ICallback _arg1;
          _arg1 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.printText(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_printTextWithFont:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _arg1;
          _arg1 = data.readString();
          float _arg2;
          _arg2 = data.readFloat();
          woyou.aidlservice.jiuiv5.ICallback _arg3;
          _arg3 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.printTextWithFont(_arg0, _arg1, _arg2, _arg3);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_printOriginalText:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          woyou.aidlservice.jiuiv5.ICallback _arg1;
          _arg1 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.printOriginalText(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_printColumnsText:
        {
          java.lang.String[] _arg0;
          _arg0 = data.createStringArray();
          int[] _arg1;
          _arg1 = data.createIntArray();
          int[] _arg2;
          _arg2 = data.createIntArray();
          woyou.aidlservice.jiuiv5.ICallback _arg3;
          _arg3 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.printColumnsText(_arg0, _arg1, _arg2, _arg3);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_printBitmap:
        {
          android.graphics.Bitmap _arg0;
          _arg0 = _Parcel.readTypedObject(data, android.graphics.Bitmap.CREATOR);
          woyou.aidlservice.jiuiv5.ICallback _arg1;
          _arg1 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.printBitmap(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_printBarCode:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          int _arg1;
          _arg1 = data.readInt();
          int _arg2;
          _arg2 = data.readInt();
          int _arg3;
          _arg3 = data.readInt();
          int _arg4;
          _arg4 = data.readInt();
          woyou.aidlservice.jiuiv5.ICallback _arg5;
          _arg5 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.printBarCode(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_printQRCode:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          int _arg1;
          _arg1 = data.readInt();
          int _arg2;
          _arg2 = data.readInt();
          woyou.aidlservice.jiuiv5.ICallback _arg3;
          _arg3 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.printQRCode(_arg0, _arg1, _arg2, _arg3);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_lineWrap:
        {
          int _arg0;
          _arg0 = data.readInt();
          woyou.aidlservice.jiuiv5.ICallback _arg1;
          _arg1 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.lineWrap(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_cutpaper:
        {
          woyou.aidlservice.jiuiv5.ICallback _arg0;
          _arg0 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.cutpaper(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_sendRAWData2:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          woyou.aidlservice.jiuiv5.ICallback _arg1;
          _arg1 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.sendRAWData2(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_setPrinterInit:
        {
          woyou.aidlservice.jiuiv5.ICallback _arg0;
          _arg0 = woyou.aidlservice.jiuiv5.ICallback.Stub.asInterface(data.readStrongBinder());
          this.setPrinterInit(_arg0);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements woyou.aidlservice.jiuiv5.IWoyouService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void printerInit(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_printerInit, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void printerSelfChecking(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_printerSelfChecking, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public java.lang.String getPrinterSerialNo() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPrinterSerialNo, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String getPrinterVersion() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPrinterVersion, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String getPrinterModal() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPrinterModal, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String getServiceVersion() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getServiceVersion, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int updatePrinterState() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_updatePrinterState, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int getPrinterState() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getPrinterState, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void setPrinterStyle(int key, int value) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(key);
          _data.writeInt(value);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setPrinterStyle, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void sendRAWData(byte[] data, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeByteArray(data);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_sendRAWData, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void setAlignment(int alignment, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(alignment);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setAlignment, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void setFontName(java.lang.String typeface, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(typeface);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setFontName, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void setFontSize(float fontsize, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeFloat(fontsize);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setFontSize, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void printText(java.lang.String text, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(text);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_printText, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void printTextWithFont(java.lang.String text, java.lang.String typeface, float fontsize, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(text);
          _data.writeString(typeface);
          _data.writeFloat(fontsize);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_printTextWithFont, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void printOriginalText(java.lang.String text, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(text);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_printOriginalText, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void printColumnsText(java.lang.String[] colsTextArr, int[] colsWidthArr, int[] colsAlign, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringArray(colsTextArr);
          _data.writeIntArray(colsWidthArr);
          _data.writeIntArray(colsAlign);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_printColumnsText, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void printBitmap(android.graphics.Bitmap bitmap, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, bitmap, 0);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_printBitmap, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void printBarCode(java.lang.String data, int symbology, int height, int width, int textPosition, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(data);
          _data.writeInt(symbology);
          _data.writeInt(height);
          _data.writeInt(width);
          _data.writeInt(textPosition);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_printBarCode, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void printQRCode(java.lang.String data, int modulesize, int errorlevel, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(data);
          _data.writeInt(modulesize);
          _data.writeInt(errorlevel);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_printQRCode, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void lineWrap(int n, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(n);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_lineWrap, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void cutpaper(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_cutpaper, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void sendRAWData2(java.lang.String base64Data, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(base64Data);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_sendRAWData2, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void setPrinterInit(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setPrinterInit, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_printerInit = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_printerSelfChecking = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_getPrinterSerialNo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_getPrinterVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_getPrinterModal = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_getServiceVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_updatePrinterState = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_getPrinterState = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_setPrinterStyle = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_sendRAWData = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_setAlignment = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
    static final int TRANSACTION_setFontName = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
    static final int TRANSACTION_setFontSize = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
    static final int TRANSACTION_printText = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
    static final int TRANSACTION_printTextWithFont = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
    static final int TRANSACTION_printOriginalText = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
    static final int TRANSACTION_printColumnsText = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
    static final int TRANSACTION_printBitmap = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
    static final int TRANSACTION_printBarCode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 18);
    static final int TRANSACTION_printQRCode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 19);
    static final int TRANSACTION_lineWrap = (android.os.IBinder.FIRST_CALL_TRANSACTION + 20);
    static final int TRANSACTION_cutpaper = (android.os.IBinder.FIRST_CALL_TRANSACTION + 21);
    static final int TRANSACTION_sendRAWData2 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 22);
    static final int TRANSACTION_setPrinterInit = (android.os.IBinder.FIRST_CALL_TRANSACTION + 23);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "woyou.aidlservice.jiuiv5.IWoyouService";
  public void printerInit(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void printerSelfChecking(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public java.lang.String getPrinterSerialNo() throws android.os.RemoteException;
  public java.lang.String getPrinterVersion() throws android.os.RemoteException;
  public java.lang.String getPrinterModal() throws android.os.RemoteException;
  public java.lang.String getServiceVersion() throws android.os.RemoteException;
  public int updatePrinterState() throws android.os.RemoteException;
  public int getPrinterState() throws android.os.RemoteException;
  public void setPrinterStyle(int key, int value) throws android.os.RemoteException;
  public void sendRAWData(byte[] data, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void setAlignment(int alignment, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void setFontName(java.lang.String typeface, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void setFontSize(float fontsize, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void printText(java.lang.String text, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void printTextWithFont(java.lang.String text, java.lang.String typeface, float fontsize, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void printOriginalText(java.lang.String text, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void printColumnsText(java.lang.String[] colsTextArr, int[] colsWidthArr, int[] colsAlign, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void printBitmap(android.graphics.Bitmap bitmap, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void printBarCode(java.lang.String data, int symbology, int height, int width, int textPosition, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void printQRCode(java.lang.String data, int modulesize, int errorlevel, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void lineWrap(int n, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void cutpaper(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void sendRAWData2(java.lang.String base64Data, woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  public void setPrinterInit(woyou.aidlservice.jiuiv5.ICallback callback) throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
