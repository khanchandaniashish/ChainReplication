// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: chain.proto

package edu.sjsu.cs249.chain;

public interface UpdateRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:chain.UpdateRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string key = 1;</code>
   * @return The key.
   */
  java.lang.String getKey();
  /**
   * <code>string key = 1;</code>
   * @return The bytes for key.
   */
  com.google.protobuf.ByteString
      getKeyBytes();

  /**
   * <code>int32 newValue = 2;</code>
   * @return The newValue.
   */
  int getNewValue();

  /**
   * <code>uint32 xid = 3;</code>
   * @return The xid.
   */
  int getXid();
}
