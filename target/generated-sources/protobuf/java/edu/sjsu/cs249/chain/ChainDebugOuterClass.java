// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: chain_debug.proto

package edu.sjsu.cs249.chain;

public final class ChainDebugOuterClass {
  private ChainDebugOuterClass() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_chain_ChainDebugRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_chain_ChainDebugRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_chain_ChainDebugResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_chain_ChainDebugResponse_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_chain_ChainDebugResponse_StateEntry_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_chain_ChainDebugResponse_StateEntry_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_chain_ExitRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_chain_ExitRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_chain_ExitResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_chain_ExitResponse_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\021chain_debug.proto\022\005chain\032\013chain.proto\"" +
      "\023\n\021ChainDebugRequest\"\266\001\n\022ChainDebugRespo" +
      "nse\0223\n\005state\030\001 \003(\0132$.chain.ChainDebugRes" +
      "ponse.StateEntry\022\013\n\003xid\030\002 \001(\r\022\"\n\004sent\030\003 " +
      "\003(\0132\024.chain.UpdateRequest\022\014\n\004logs\030\004 \003(\t\032" +
      ",\n\nStateEntry\022\013\n\003key\030\001 \001(\t\022\r\n\005value\030\002 \001(" +
      "\r:\0028\001\"\r\n\013ExitRequest\"\016\n\014ExitResponse2{\n\n" +
      "ChainDebug\022<\n\005debug\022\030.chain.ChainDebugRe" +
      "quest\032\031.chain.ChainDebugResponse\022/\n\004exit" +
      "\022\022.chain.ExitRequest\032\023.chain.ExitRespons" +
      "eB\030\n\024edu.sjsu.cs249.chainP\001b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
          edu.sjsu.cs249.chain.Chain.getDescriptor(),
        });
    internal_static_chain_ChainDebugRequest_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_chain_ChainDebugRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_chain_ChainDebugRequest_descriptor,
        new java.lang.String[] { });
    internal_static_chain_ChainDebugResponse_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_chain_ChainDebugResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_chain_ChainDebugResponse_descriptor,
        new java.lang.String[] { "State", "Xid", "Sent", "Logs", });
    internal_static_chain_ChainDebugResponse_StateEntry_descriptor =
      internal_static_chain_ChainDebugResponse_descriptor.getNestedTypes().get(0);
    internal_static_chain_ChainDebugResponse_StateEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_chain_ChainDebugResponse_StateEntry_descriptor,
        new java.lang.String[] { "Key", "Value", });
    internal_static_chain_ExitRequest_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_chain_ExitRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_chain_ExitRequest_descriptor,
        new java.lang.String[] { });
    internal_static_chain_ExitResponse_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_chain_ExitResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_chain_ExitResponse_descriptor,
        new java.lang.String[] { });
    edu.sjsu.cs249.chain.Chain.getDescriptor();
  }

  // @@protoc_insertion_point(outer_class_scope)
}
