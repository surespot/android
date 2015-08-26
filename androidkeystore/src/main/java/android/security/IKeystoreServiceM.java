/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: IKeystoreService.aidl
 */
package android.security;
/**
 * This must be kept manually in sync with system/security/keystore until AIDL
 * can generate both Java and C++ bindings.
 *
 * @hide
 */
public interface IKeystoreServiceM extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements android.security.IKeystoreServiceM
{
private static final java.lang.String DESCRIPTOR = "android.security.IKeystoreService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an android.security.IKeystoreService interface,
 * generating a proxy if needed.
 */
public static android.security.IKeystoreServiceM asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof android.security.IKeystoreServiceM))) {
return ((android.security.IKeystoreServiceM)iin);
}
return new android.security.IKeystoreServiceM.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_getState:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
int _result = this.getState(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_get:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
byte[] _result = this.get(_arg0);
reply.writeNoException();
reply.writeByteArray(_result);
return true;
}
case TRANSACTION_insert:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
byte[] _arg1;
_arg1 = data.createByteArray();
int _arg2;
_arg2 = data.readInt();
int _arg3;
_arg3 = data.readInt();
int _result = this.insert(_arg0, _arg1, _arg2, _arg3);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_del:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _arg1;
_arg1 = data.readInt();
int _result = this.del(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_exist:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _arg1;
_arg1 = data.readInt();
int _result = this.exist(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_list:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _arg1;
_arg1 = data.readInt();
java.lang.String[] _result = this.list(_arg0, _arg1);
reply.writeNoException();
reply.writeStringArray(_result);
return true;
}
case TRANSACTION_reset:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.reset();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_onUserPasswordChanged:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
java.lang.String _arg1;
_arg1 = data.readString();
int _result = this.onUserPasswordChanged(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_lock:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
int _result = this.lock(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_unlock:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
java.lang.String _arg1;
_arg1 = data.readString();
int _result = this.unlock(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_isEmpty:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
int _result = this.isEmpty(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_generate:
{
data.enforceInterface(DESCRIPTOR);
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
android.security.KeystoreArguments _arg5;
if ((0!=data.readInt())) {
_arg5 = android.security.KeystoreArguments.CREATOR.createFromParcel(data);
}
else {
_arg5 = null;
}
int _result = this.generate(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_import_key:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
byte[] _arg1;
_arg1 = data.createByteArray();
int _arg2;
_arg2 = data.readInt();
int _arg3;
_arg3 = data.readInt();
int _result = this.import_key(_arg0, _arg1, _arg2, _arg3);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_sign:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
byte[] _arg1;
_arg1 = data.createByteArray();
byte[] _result = this.sign(_arg0, _arg1);
reply.writeNoException();
reply.writeByteArray(_result);
return true;
}
case TRANSACTION_verify:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
byte[] _arg1;
_arg1 = data.createByteArray();
byte[] _arg2;
_arg2 = data.createByteArray();
int _result = this.verify(_arg0, _arg1, _arg2);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_get_pubkey:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
byte[] _result = this.get_pubkey(_arg0);
reply.writeNoException();
reply.writeByteArray(_result);
return true;
}
case TRANSACTION_grant:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _arg1;
_arg1 = data.readInt();
int _result = this.grant(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_ungrant:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _arg1;
_arg1 = data.readInt();
int _result = this.ungrant(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_getmtime:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
long _result = this.getmtime(_arg0);
reply.writeNoException();
reply.writeLong(_result);
return true;
}
case TRANSACTION_duplicate:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _arg1;
_arg1 = data.readInt();
java.lang.String _arg2;
_arg2 = data.readString();
int _arg3;
_arg3 = data.readInt();
int _result = this.duplicate(_arg0, _arg1, _arg2, _arg3);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_is_hardware_backed:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _result = this.is_hardware_backed(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_clear_uid:
{
data.enforceInterface(DESCRIPTOR);
long _arg0;
_arg0 = data.readLong();
int _result = this.clear_uid(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_addRngEntropy:
{
data.enforceInterface(DESCRIPTOR);
byte[] _arg0;
_arg0 = data.createByteArray();
int _result = this.addRngEntropy(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_generateKey:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
android.security.keymaster.KeymasterArguments _arg1;
if ((0!=data.readInt())) {
_arg1 = android.security.keymaster.KeymasterArguments.CREATOR.createFromParcel(data);
}
else {
_arg1 = null;
}
byte[] _arg2;
_arg2 = data.createByteArray();
int _arg3;
_arg3 = data.readInt();
int _arg4;
_arg4 = data.readInt();
android.security.keymaster.KeyCharacteristics _arg5;
_arg5 = new android.security.keymaster.KeyCharacteristics();
int _result = this.generateKey(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5);
reply.writeNoException();
reply.writeInt(_result);
if ((_arg5!=null)) {
reply.writeInt(1);
_arg5.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
}
else {
reply.writeInt(0);
}
return true;
}
case TRANSACTION_getKeyCharacteristics:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
android.security.keymaster.KeymasterBlob _arg1;
if ((0!=data.readInt())) {
_arg1 = android.security.keymaster.KeymasterBlob.CREATOR.createFromParcel(data);
}
else {
_arg1 = null;
}
android.security.keymaster.KeymasterBlob _arg2;
if ((0!=data.readInt())) {
_arg2 = android.security.keymaster.KeymasterBlob.CREATOR.createFromParcel(data);
}
else {
_arg2 = null;
}
android.security.keymaster.KeyCharacteristics _arg3;
_arg3 = new android.security.keymaster.KeyCharacteristics();
int _result = this.getKeyCharacteristics(_arg0, _arg1, _arg2, _arg3);
reply.writeNoException();
reply.writeInt(_result);
if ((_arg3!=null)) {
reply.writeInt(1);
_arg3.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
}
else {
reply.writeInt(0);
}
return true;
}
case TRANSACTION_importKey:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
android.security.keymaster.KeymasterArguments _arg1;
if ((0!=data.readInt())) {
_arg1 = android.security.keymaster.KeymasterArguments.CREATOR.createFromParcel(data);
}
else {
_arg1 = null;
}
int _arg2;
_arg2 = data.readInt();
byte[] _arg3;
_arg3 = data.createByteArray();
int _arg4;
_arg4 = data.readInt();
int _arg5;
_arg5 = data.readInt();
android.security.keymaster.KeyCharacteristics _arg6;
_arg6 = new android.security.keymaster.KeyCharacteristics();
int _result = this.importKey(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5, _arg6);
reply.writeNoException();
reply.writeInt(_result);
if ((_arg6!=null)) {
reply.writeInt(1);
_arg6.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
}
else {
reply.writeInt(0);
}
return true;
}
case TRANSACTION_exportKey:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _arg1;
_arg1 = data.readInt();
android.security.keymaster.KeymasterBlob _arg2;
if ((0!=data.readInt())) {
_arg2 = android.security.keymaster.KeymasterBlob.CREATOR.createFromParcel(data);
}
else {
_arg2 = null;
}
android.security.keymaster.KeymasterBlob _arg3;
if ((0!=data.readInt())) {
_arg3 = android.security.keymaster.KeymasterBlob.CREATOR.createFromParcel(data);
}
else {
_arg3 = null;
}
android.security.keymaster.ExportResult _result = this.exportKey(_arg0, _arg1, _arg2, _arg3);
reply.writeNoException();
if ((_result!=null)) {
reply.writeInt(1);
_result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
}
else {
reply.writeInt(0);
}
return true;
}
case TRANSACTION_begin:
{
data.enforceInterface(DESCRIPTOR);
android.os.IBinder _arg0;
_arg0 = data.readStrongBinder();
java.lang.String _arg1;
_arg1 = data.readString();
int _arg2;
_arg2 = data.readInt();
boolean _arg3;
_arg3 = (0!=data.readInt());
android.security.keymaster.KeymasterArguments _arg4;
if ((0!=data.readInt())) {
_arg4 = android.security.keymaster.KeymasterArguments.CREATOR.createFromParcel(data);
}
else {
_arg4 = null;
}
byte[] _arg5;
_arg5 = data.createByteArray();
android.security.keymaster.KeymasterArguments _arg6;
_arg6 = new android.security.keymaster.KeymasterArguments();
android.security.keymaster.OperationResult _result = this.begin(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5, _arg6);
reply.writeNoException();
if ((_result!=null)) {
reply.writeInt(1);
_result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
}
else {
reply.writeInt(0);
}
if ((_arg6!=null)) {
reply.writeInt(1);
_arg6.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
}
else {
reply.writeInt(0);
}
return true;
}
case TRANSACTION_update:
{
data.enforceInterface(DESCRIPTOR);
android.os.IBinder _arg0;
_arg0 = data.readStrongBinder();
android.security.keymaster.KeymasterArguments _arg1;
if ((0!=data.readInt())) {
_arg1 = android.security.keymaster.KeymasterArguments.CREATOR.createFromParcel(data);
}
else {
_arg1 = null;
}
byte[] _arg2;
_arg2 = data.createByteArray();
android.security.keymaster.OperationResult _result = this.update(_arg0, _arg1, _arg2);
reply.writeNoException();
if ((_result!=null)) {
reply.writeInt(1);
_result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
}
else {
reply.writeInt(0);
}
return true;
}
case TRANSACTION_finish:
{
data.enforceInterface(DESCRIPTOR);
android.os.IBinder _arg0;
_arg0 = data.readStrongBinder();
android.security.keymaster.KeymasterArguments _arg1;
if ((0!=data.readInt())) {
_arg1 = android.security.keymaster.KeymasterArguments.CREATOR.createFromParcel(data);
}
else {
_arg1 = null;
}
byte[] _arg2;
_arg2 = data.createByteArray();
byte[] _arg3;
_arg3 = data.createByteArray();
android.security.keymaster.OperationResult _result = this.finish(_arg0, _arg1, _arg2, _arg3);
reply.writeNoException();
if ((_result!=null)) {
reply.writeInt(1);
_result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
}
else {
reply.writeInt(0);
}
return true;
}
case TRANSACTION_abort:
{
data.enforceInterface(DESCRIPTOR);
android.os.IBinder _arg0;
_arg0 = data.readStrongBinder();
int _result = this.abort(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_isOperationAuthorized:
{
data.enforceInterface(DESCRIPTOR);
android.os.IBinder _arg0;
_arg0 = data.readStrongBinder();
boolean _result = this.isOperationAuthorized(_arg0);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_addAuthToken:
{
data.enforceInterface(DESCRIPTOR);
byte[] _arg0;
_arg0 = data.createByteArray();
int _result = this.addAuthToken(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_onUserAdded:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
int _arg1;
_arg1 = data.readInt();
int _result = this.onUserAdded(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_onUserRemoved:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
int _result = this.onUserRemoved(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements android.security.IKeystoreServiceM
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
@Override public int getState(int userId) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(userId);
mRemote.transact(Stub.TRANSACTION_getState, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public byte[] get(java.lang.String name) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
byte[] _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
mRemote.transact(Stub.TRANSACTION_get, _data, _reply, 0);
_reply.readException();
_result = _reply.createByteArray();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int insert(java.lang.String name, byte[] item, int uid, int flags) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
_data.writeByteArray(item);
_data.writeInt(uid);
_data.writeInt(flags);
mRemote.transact(Stub.TRANSACTION_insert, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int del(java.lang.String name, int uid) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
_data.writeInt(uid);
mRemote.transact(Stub.TRANSACTION_del, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int exist(java.lang.String name, int uid) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
_data.writeInt(uid);
mRemote.transact(Stub.TRANSACTION_exist, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public java.lang.String[] list(java.lang.String namePrefix, int uid) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String[] _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(namePrefix);
_data.writeInt(uid);
mRemote.transact(Stub.TRANSACTION_list, _data, _reply, 0);
_reply.readException();
_result = _reply.createStringArray();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int reset() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_reset, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int onUserPasswordChanged(int userId, java.lang.String newPassword) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(userId);
_data.writeString(newPassword);
mRemote.transact(Stub.TRANSACTION_onUserPasswordChanged, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int lock(int userId) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(userId);
mRemote.transact(Stub.TRANSACTION_lock, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int unlock(int userId, java.lang.String userPassword) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(userId);
_data.writeString(userPassword);
mRemote.transact(Stub.TRANSACTION_unlock, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int isEmpty(int userId) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(userId);
mRemote.transact(Stub.TRANSACTION_isEmpty, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int generate(java.lang.String name, int uid, int keyType, int keySize, int flags, android.security.KeystoreArguments args) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
_data.writeInt(uid);
_data.writeInt(keyType);
_data.writeInt(keySize);
_data.writeInt(flags);
if ((args!=null)) {
_data.writeInt(1);
args.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_generate, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int import_key(java.lang.String name, byte[] data, int uid, int flags) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
_data.writeByteArray(data);
_data.writeInt(uid);
_data.writeInt(flags);
mRemote.transact(Stub.TRANSACTION_import_key, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public byte[] sign(java.lang.String name, byte[] data) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
byte[] _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
_data.writeByteArray(data);
mRemote.transact(Stub.TRANSACTION_sign, _data, _reply, 0);
_reply.readException();
_result = _reply.createByteArray();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int verify(java.lang.String name, byte[] data, byte[] signature) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
_data.writeByteArray(data);
_data.writeByteArray(signature);
mRemote.transact(Stub.TRANSACTION_verify, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public byte[] get_pubkey(java.lang.String name) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
byte[] _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
mRemote.transact(Stub.TRANSACTION_get_pubkey, _data, _reply, 0);
_reply.readException();
_result = _reply.createByteArray();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int grant(java.lang.String name, int granteeUid) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
_data.writeInt(granteeUid);
mRemote.transact(Stub.TRANSACTION_grant, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int ungrant(java.lang.String name, int granteeUid) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
_data.writeInt(granteeUid);
mRemote.transact(Stub.TRANSACTION_ungrant, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public long getmtime(java.lang.String name) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(name);
mRemote.transact(Stub.TRANSACTION_getmtime, _data, _reply, 0);
_reply.readException();
_result = _reply.readLong();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int duplicate(java.lang.String srcKey, int srcUid, java.lang.String destKey, int destUid) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(srcKey);
_data.writeInt(srcUid);
_data.writeString(destKey);
_data.writeInt(destUid);
mRemote.transact(Stub.TRANSACTION_duplicate, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int is_hardware_backed(java.lang.String string) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(string);
mRemote.transact(Stub.TRANSACTION_is_hardware_backed, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int clear_uid(long uid) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLong(uid);
mRemote.transact(Stub.TRANSACTION_clear_uid, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
// Keymaster 0.4 methods

@Override public int addRngEntropy(byte[] data) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeByteArray(data);
mRemote.transact(Stub.TRANSACTION_addRngEntropy, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int generateKey(java.lang.String alias, android.security.keymaster.KeymasterArguments arguments, byte[] entropy, int uid, int flags, android.security.keymaster.KeyCharacteristics characteristics) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(alias);
if ((arguments!=null)) {
_data.writeInt(1);
arguments.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
_data.writeByteArray(entropy);
_data.writeInt(uid);
_data.writeInt(flags);
mRemote.transact(Stub.TRANSACTION_generateKey, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
if ((0!=_reply.readInt())) {
characteristics.readFromParcel(_reply);
}
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int getKeyCharacteristics(java.lang.String alias, android.security.keymaster.KeymasterBlob clientId, android.security.keymaster.KeymasterBlob appId, android.security.keymaster.KeyCharacteristics characteristics) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(alias);
if ((clientId!=null)) {
_data.writeInt(1);
clientId.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
if ((appId!=null)) {
_data.writeInt(1);
appId.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_getKeyCharacteristics, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
if ((0!=_reply.readInt())) {
characteristics.readFromParcel(_reply);
}
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int importKey(java.lang.String alias, android.security.keymaster.KeymasterArguments arguments, int format, byte[] keyData, int uid, int flags, android.security.keymaster.KeyCharacteristics characteristics) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(alias);
if ((arguments!=null)) {
_data.writeInt(1);
arguments.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
_data.writeInt(format);
_data.writeByteArray(keyData);
_data.writeInt(uid);
_data.writeInt(flags);
mRemote.transact(Stub.TRANSACTION_importKey, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
if ((0!=_reply.readInt())) {
characteristics.readFromParcel(_reply);
}
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public android.security.keymaster.ExportResult exportKey(java.lang.String alias, int format, android.security.keymaster.KeymasterBlob clientId, android.security.keymaster.KeymasterBlob appId) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
android.security.keymaster.ExportResult _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(alias);
_data.writeInt(format);
if ((clientId!=null)) {
_data.writeInt(1);
clientId.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
if ((appId!=null)) {
_data.writeInt(1);
appId.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_exportKey, _data, _reply, 0);
_reply.readException();
if ((0!=_reply.readInt())) {
_result = android.security.keymaster.ExportResult.CREATOR.createFromParcel(_reply);
}
else {
_result = null;
}
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public android.security.keymaster.OperationResult begin(android.os.IBinder appToken, java.lang.String alias, int purpose, boolean pruneable, android.security.keymaster.KeymasterArguments params, byte[] entropy, android.security.keymaster.KeymasterArguments operationParams) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
android.security.keymaster.OperationResult _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder(appToken);
_data.writeString(alias);
_data.writeInt(purpose);
_data.writeInt(((pruneable)?(1):(0)));
if ((params!=null)) {
_data.writeInt(1);
params.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
_data.writeByteArray(entropy);
mRemote.transact(Stub.TRANSACTION_begin, _data, _reply, 0);
_reply.readException();
if ((0!=_reply.readInt())) {
_result = android.security.keymaster.OperationResult.CREATOR.createFromParcel(_reply);
}
else {
_result = null;
}
if ((0!=_reply.readInt())) {
operationParams.readFromParcel(_reply);
}
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public android.security.keymaster.OperationResult update(android.os.IBinder token, android.security.keymaster.KeymasterArguments params, byte[] input) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
android.security.keymaster.OperationResult _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder(token);
if ((params!=null)) {
_data.writeInt(1);
params.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
_data.writeByteArray(input);
mRemote.transact(Stub.TRANSACTION_update, _data, _reply, 0);
_reply.readException();
if ((0!=_reply.readInt())) {
_result = android.security.keymaster.OperationResult.CREATOR.createFromParcel(_reply);
}
else {
_result = null;
}
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public android.security.keymaster.OperationResult finish(android.os.IBinder token, android.security.keymaster.KeymasterArguments params, byte[] signature, byte[] entropy) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
android.security.keymaster.OperationResult _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder(token);
if ((params!=null)) {
_data.writeInt(1);
params.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
_data.writeByteArray(signature);
_data.writeByteArray(entropy);
mRemote.transact(Stub.TRANSACTION_finish, _data, _reply, 0);
_reply.readException();
if ((0!=_reply.readInt())) {
_result = android.security.keymaster.OperationResult.CREATOR.createFromParcel(_reply);
}
else {
_result = null;
}
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int abort(android.os.IBinder handle) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder(handle);
mRemote.transact(Stub.TRANSACTION_abort, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public boolean isOperationAuthorized(android.os.IBinder token) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder(token);
mRemote.transact(Stub.TRANSACTION_isOperationAuthorized, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int addAuthToken(byte[] authToken) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeByteArray(authToken);
mRemote.transact(Stub.TRANSACTION_addAuthToken, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int onUserAdded(int userId, int parentId) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(userId);
_data.writeInt(parentId);
mRemote.transact(Stub.TRANSACTION_onUserAdded, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int onUserRemoved(int userId) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(userId);
mRemote.transact(Stub.TRANSACTION_onUserRemoved, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_getState = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_get = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_insert = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_del = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_exist = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_list = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
static final int TRANSACTION_reset = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
static final int TRANSACTION_onUserPasswordChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
static final int TRANSACTION_lock = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
static final int TRANSACTION_unlock = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
static final int TRANSACTION_isEmpty = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
static final int TRANSACTION_generate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
static final int TRANSACTION_import_key = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
static final int TRANSACTION_sign = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
static final int TRANSACTION_verify = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
static final int TRANSACTION_get_pubkey = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
static final int TRANSACTION_grant = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
static final int TRANSACTION_ungrant = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
static final int TRANSACTION_getmtime = (android.os.IBinder.FIRST_CALL_TRANSACTION + 18);
static final int TRANSACTION_duplicate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 19);
static final int TRANSACTION_is_hardware_backed = (android.os.IBinder.FIRST_CALL_TRANSACTION + 20);
static final int TRANSACTION_clear_uid = (android.os.IBinder.FIRST_CALL_TRANSACTION + 21);
static final int TRANSACTION_addRngEntropy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 22);
static final int TRANSACTION_generateKey = (android.os.IBinder.FIRST_CALL_TRANSACTION + 23);
static final int TRANSACTION_getKeyCharacteristics = (android.os.IBinder.FIRST_CALL_TRANSACTION + 24);
static final int TRANSACTION_importKey = (android.os.IBinder.FIRST_CALL_TRANSACTION + 25);
static final int TRANSACTION_exportKey = (android.os.IBinder.FIRST_CALL_TRANSACTION + 26);
static final int TRANSACTION_begin = (android.os.IBinder.FIRST_CALL_TRANSACTION + 27);
static final int TRANSACTION_update = (android.os.IBinder.FIRST_CALL_TRANSACTION + 28);
static final int TRANSACTION_finish = (android.os.IBinder.FIRST_CALL_TRANSACTION + 29);
static final int TRANSACTION_abort = (android.os.IBinder.FIRST_CALL_TRANSACTION + 30);
static final int TRANSACTION_isOperationAuthorized = (android.os.IBinder.FIRST_CALL_TRANSACTION + 31);
static final int TRANSACTION_addAuthToken = (android.os.IBinder.FIRST_CALL_TRANSACTION + 32);
static final int TRANSACTION_onUserAdded = (android.os.IBinder.FIRST_CALL_TRANSACTION + 33);
static final int TRANSACTION_onUserRemoved = (android.os.IBinder.FIRST_CALL_TRANSACTION + 34);
}
public int getState(int userId) throws android.os.RemoteException;
public byte[] get(java.lang.String name) throws android.os.RemoteException;
public int insert(java.lang.String name, byte[] item, int uid, int flags) throws android.os.RemoteException;
public int del(java.lang.String name, int uid) throws android.os.RemoteException;
public int exist(java.lang.String name, int uid) throws android.os.RemoteException;
public java.lang.String[] list(java.lang.String namePrefix, int uid) throws android.os.RemoteException;
public int reset() throws android.os.RemoteException;
public int onUserPasswordChanged(int userId, java.lang.String newPassword) throws android.os.RemoteException;
public int lock(int userId) throws android.os.RemoteException;
public int unlock(int userId, java.lang.String userPassword) throws android.os.RemoteException;
public int isEmpty(int userId) throws android.os.RemoteException;
public int generate(java.lang.String name, int uid, int keyType, int keySize, int flags, android.security.KeystoreArguments args) throws android.os.RemoteException;
public int import_key(java.lang.String name, byte[] data, int uid, int flags) throws android.os.RemoteException;
public byte[] sign(java.lang.String name, byte[] data) throws android.os.RemoteException;
public int verify(java.lang.String name, byte[] data, byte[] signature) throws android.os.RemoteException;
public byte[] get_pubkey(java.lang.String name) throws android.os.RemoteException;
public int grant(java.lang.String name, int granteeUid) throws android.os.RemoteException;
public int ungrant(java.lang.String name, int granteeUid) throws android.os.RemoteException;
public long getmtime(java.lang.String name) throws android.os.RemoteException;
public int duplicate(java.lang.String srcKey, int srcUid, java.lang.String destKey, int destUid) throws android.os.RemoteException;
public int is_hardware_backed(java.lang.String string) throws android.os.RemoteException;
public int clear_uid(long uid) throws android.os.RemoteException;
// Keymaster 0.4 methods

public int addRngEntropy(byte[] data) throws android.os.RemoteException;
public int generateKey(java.lang.String alias, android.security.keymaster.KeymasterArguments arguments, byte[] entropy, int uid, int flags, android.security.keymaster.KeyCharacteristics characteristics) throws android.os.RemoteException;
public int getKeyCharacteristics(java.lang.String alias, android.security.keymaster.KeymasterBlob clientId, android.security.keymaster.KeymasterBlob appId, android.security.keymaster.KeyCharacteristics characteristics) throws android.os.RemoteException;
public int importKey(java.lang.String alias, android.security.keymaster.KeymasterArguments arguments, int format, byte[] keyData, int uid, int flags, android.security.keymaster.KeyCharacteristics characteristics) throws android.os.RemoteException;
public android.security.keymaster.ExportResult exportKey(java.lang.String alias, int format, android.security.keymaster.KeymasterBlob clientId, android.security.keymaster.KeymasterBlob appId) throws android.os.RemoteException;
public android.security.keymaster.OperationResult begin(android.os.IBinder appToken, java.lang.String alias, int purpose, boolean pruneable, android.security.keymaster.KeymasterArguments params, byte[] entropy, android.security.keymaster.KeymasterArguments operationParams) throws android.os.RemoteException;
public android.security.keymaster.OperationResult update(android.os.IBinder token, android.security.keymaster.KeymasterArguments params, byte[] input) throws android.os.RemoteException;
public android.security.keymaster.OperationResult finish(android.os.IBinder token, android.security.keymaster.KeymasterArguments params, byte[] signature, byte[] entropy) throws android.os.RemoteException;
public int abort(android.os.IBinder handle) throws android.os.RemoteException;
public boolean isOperationAuthorized(android.os.IBinder token) throws android.os.RemoteException;
public int addAuthToken(byte[] authToken) throws android.os.RemoteException;
public int onUserAdded(int userId, int parentId) throws android.os.RemoteException;
public int onUserRemoved(int userId) throws android.os.RemoteException;
}
