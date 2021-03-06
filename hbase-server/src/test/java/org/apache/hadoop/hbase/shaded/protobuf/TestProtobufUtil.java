/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.shaded.protobuf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellBuilderFactory;
import org.apache.hadoop.hbase.CellBuilderType;
import org.apache.hadoop.hbase.CellComparator;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.ProcedureInfo;
import org.apache.hadoop.hbase.ProcedureState;
import org.apache.hadoop.hbase.ByteBufferKeyValue;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.procedure2.LockInfo;
import org.apache.hadoop.hbase.shaded.com.google.protobuf.ByteString;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos.Column;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos.MutationProto;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos.MutationProto.ColumnValue;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos.MutationProto.ColumnValue.QualifierValue;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos.MutationProto.DeleteType;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos.MutationProto.MutationType;
import org.apache.hadoop.hbase.shaded.protobuf.generated.LockServiceProtos;
import org.apache.hadoop.hbase.shaded.protobuf.generated.HBaseProtos.NameBytesPair;
import org.apache.hadoop.hbase.shaded.protobuf.generated.CellProtos;

import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;
import org.junit.experimental.categories.Category;


import java.io.IOException;
import java.nio.ByteBuffer;

@Category(SmallTests.class)
public class TestProtobufUtil {
  @Test
  public void testException() throws IOException {
    NameBytesPair.Builder builder = NameBytesPair.newBuilder();
    final String omg = "OMG!!!";
    builder.setName("java.io.IOException");
    builder.setValue(ByteString.copyFrom(Bytes.toBytes(omg)));
    Throwable t = ProtobufUtil.toException(builder.build());
    assertEquals(omg, t.getMessage());
    builder.clear();
    builder.setName("org.apache.hadoop.ipc.RemoteException");
    builder.setValue(ByteString.copyFrom(Bytes.toBytes(omg)));
    t = ProtobufUtil.toException(builder.build());
    assertEquals(omg, t.getMessage());
  }

  /**
   * Test basic Get conversions.
   *
   * @throws IOException
   */
  @Test
  public void testGet() throws IOException {
    ClientProtos.Get.Builder getBuilder = ClientProtos.Get.newBuilder();
    getBuilder.setRow(ByteString.copyFromUtf8("row"));
    Column.Builder columnBuilder = Column.newBuilder();
    columnBuilder.setFamily(ByteString.copyFromUtf8("f1"));
    columnBuilder.addQualifier(ByteString.copyFromUtf8("c1"));
    columnBuilder.addQualifier(ByteString.copyFromUtf8("c2"));
    getBuilder.addColumn(columnBuilder.build());

    columnBuilder.clear();
    columnBuilder.setFamily(ByteString.copyFromUtf8("f2"));
    getBuilder.addColumn(columnBuilder.build());
    getBuilder.setLoadColumnFamiliesOnDemand(true);
    ClientProtos.Get proto = getBuilder.build();
    // default fields
    assertEquals(1, proto.getMaxVersions());
    assertEquals(true, proto.getCacheBlocks());

    // set the default value for equal comparison
    getBuilder = ClientProtos.Get.newBuilder(proto);
    getBuilder.setMaxVersions(1);
    getBuilder.setCacheBlocks(true);

    Get get = ProtobufUtil.toGet(proto);
    assertEquals(getBuilder.build(), ProtobufUtil.toGet(get));
  }

  /**
   * Test Delete Mutate conversions.
   *
   * @throws IOException
   */
  @Test
  public void testDelete() throws IOException {
    MutationProto.Builder mutateBuilder = MutationProto.newBuilder();
    mutateBuilder.setRow(ByteString.copyFromUtf8("row"));
    mutateBuilder.setMutateType(MutationType.DELETE);
    mutateBuilder.setTimestamp(111111);
    ColumnValue.Builder valueBuilder = ColumnValue.newBuilder();
    valueBuilder.setFamily(ByteString.copyFromUtf8("f1"));
    QualifierValue.Builder qualifierBuilder = QualifierValue.newBuilder();
    qualifierBuilder.setQualifier(ByteString.copyFromUtf8("c1"));
    qualifierBuilder.setDeleteType(DeleteType.DELETE_ONE_VERSION);
    qualifierBuilder.setTimestamp(111222);
    valueBuilder.addQualifierValue(qualifierBuilder.build());
    qualifierBuilder.setQualifier(ByteString.copyFromUtf8("c2"));
    qualifierBuilder.setDeleteType(DeleteType.DELETE_MULTIPLE_VERSIONS);
    qualifierBuilder.setTimestamp(111333);
    valueBuilder.addQualifierValue(qualifierBuilder.build());
    mutateBuilder.addColumnValue(valueBuilder.build());

    MutationProto proto = mutateBuilder.build();
    // default fields
    assertEquals(MutationProto.Durability.USE_DEFAULT, proto.getDurability());

    // set the default value for equal comparison
    mutateBuilder = MutationProto.newBuilder(proto);
    mutateBuilder.setDurability(MutationProto.Durability.USE_DEFAULT);

    Delete delete = ProtobufUtil.toDelete(proto);

    // delete always have empty value,
    // add empty value to the original mutate
    for (ColumnValue.Builder column:
        mutateBuilder.getColumnValueBuilderList()) {
      for (QualifierValue.Builder qualifier:
          column.getQualifierValueBuilderList()) {
        qualifier.setValue(ByteString.EMPTY);
      }
    }
    assertEquals(mutateBuilder.build(),
      ProtobufUtil.toMutation(MutationType.DELETE, delete));
  }

  /**
   * Test Put Mutate conversions.
   *
   * @throws IOException
   */
  @Test
  public void testPut() throws IOException {
    MutationProto.Builder mutateBuilder = MutationProto.newBuilder();
    mutateBuilder.setRow(ByteString.copyFromUtf8("row"));
    mutateBuilder.setMutateType(MutationType.PUT);
    mutateBuilder.setTimestamp(111111);
    ColumnValue.Builder valueBuilder = ColumnValue.newBuilder();
    valueBuilder.setFamily(ByteString.copyFromUtf8("f1"));
    QualifierValue.Builder qualifierBuilder = QualifierValue.newBuilder();
    qualifierBuilder.setQualifier(ByteString.copyFromUtf8("c1"));
    qualifierBuilder.setValue(ByteString.copyFromUtf8("v1"));
    valueBuilder.addQualifierValue(qualifierBuilder.build());
    qualifierBuilder.setQualifier(ByteString.copyFromUtf8("c2"));
    qualifierBuilder.setValue(ByteString.copyFromUtf8("v2"));
    qualifierBuilder.setTimestamp(222222);
    valueBuilder.addQualifierValue(qualifierBuilder.build());
    mutateBuilder.addColumnValue(valueBuilder.build());

    MutationProto proto = mutateBuilder.build();
    // default fields
    assertEquals(MutationProto.Durability.USE_DEFAULT, proto.getDurability());

    // set the default value for equal comparison
    mutateBuilder = MutationProto.newBuilder(proto);
    mutateBuilder.setDurability(MutationProto.Durability.USE_DEFAULT);

    Put put = ProtobufUtil.toPut(proto);

    // put value always use the default timestamp if no
    // value level timestamp specified,
    // add the timestamp to the original mutate
    long timestamp = put.getTimeStamp();
    for (ColumnValue.Builder column:
        mutateBuilder.getColumnValueBuilderList()) {
      for (QualifierValue.Builder qualifier:
          column.getQualifierValueBuilderList()) {
        if (!qualifier.hasTimestamp()) {
          qualifier.setTimestamp(timestamp);
        }
      }
    }
    assertEquals(mutateBuilder.build(),
      ProtobufUtil.toMutation(MutationType.PUT, put));
  }

  /**
   * Test basic Scan conversions.
   *
   * @throws IOException
   */
  @Test
  public void testScan() throws IOException {
    ClientProtos.Scan.Builder scanBuilder = ClientProtos.Scan.newBuilder();
    scanBuilder.setStartRow(ByteString.copyFromUtf8("row1"));
    scanBuilder.setStopRow(ByteString.copyFromUtf8("row2"));
    Column.Builder columnBuilder = Column.newBuilder();
    columnBuilder.setFamily(ByteString.copyFromUtf8("f1"));
    columnBuilder.addQualifier(ByteString.copyFromUtf8("c1"));
    columnBuilder.addQualifier(ByteString.copyFromUtf8("c2"));
    scanBuilder.addColumn(columnBuilder.build());

    columnBuilder.clear();
    columnBuilder.setFamily(ByteString.copyFromUtf8("f2"));
    scanBuilder.addColumn(columnBuilder.build());

    ClientProtos.Scan proto = scanBuilder.build();

    // Verify default values
    assertEquals(1, proto.getMaxVersions());
    assertEquals(true, proto.getCacheBlocks());

    // Verify fields survive ClientProtos.Scan -> Scan -> ClientProtos.Scan
    // conversion
    scanBuilder = ClientProtos.Scan.newBuilder(proto);
    scanBuilder.setMaxVersions(2);
    scanBuilder.setCacheBlocks(false);
    scanBuilder.setCaching(1024);
    ClientProtos.Scan expectedProto = scanBuilder.build();

    ClientProtos.Scan actualProto = ProtobufUtil.toScan(
        ProtobufUtil.toScan(expectedProto));
    assertEquals(expectedProto, actualProto);
  }

  @Test
  public void testToCell() throws Exception {
    KeyValue kv1 =
        new KeyValue(Bytes.toBytes("aaa"), Bytes.toBytes("f1"), Bytes.toBytes("q1"), new byte[30]);
    KeyValue kv2 =
        new KeyValue(Bytes.toBytes("bbb"), Bytes.toBytes("f1"), Bytes.toBytes("q1"), new byte[30]);
    KeyValue kv3 =
        new KeyValue(Bytes.toBytes("ccc"), Bytes.toBytes("f1"), Bytes.toBytes("q1"), new byte[30]);
    byte[] arr = new byte[kv1.getLength() + kv2.getLength() + kv3.getLength()];
    System.arraycopy(kv1.getBuffer(), kv1.getOffset(), arr, 0, kv1.getLength());
    System.arraycopy(kv2.getBuffer(), kv2.getOffset(), arr, kv1.getLength(), kv2.getLength());
    System.arraycopy(kv3.getBuffer(), kv3.getOffset(), arr, kv1.getLength() + kv2.getLength(),
      kv3.getLength());
    ByteBuffer dbb = ByteBuffer.allocateDirect(arr.length);
    dbb.put(arr);
    ByteBufferKeyValue offheapKV = new ByteBufferKeyValue(dbb, kv1.getLength(), kv2.getLength());
    CellProtos.Cell cell = ProtobufUtil.toCell(offheapKV);
    Cell newOffheapKV = ProtobufUtil.toCell(CellBuilderFactory.create(CellBuilderType.SHALLOW_COPY), cell);
    assertTrue(CellComparator.COMPARATOR.compare(offheapKV, newOffheapKV) == 0);
  }

  public TestProtobufUtil() {
  }

  private static ProcedureInfo createProcedureInfo(long procId)
  {
    return new ProcedureInfo(procId, "java.lang.Object", null,
        ProcedureState.RUNNABLE, -1, null, null, 0, 0, null);
  }

  private static void assertProcedureInfoEquals(ProcedureInfo expected,
      ProcedureInfo result)
  {
    if (expected == result) {
      return;
    } else if (expected == null || result == null) {
      fail();
    }

    assertEquals(expected.getProcId(), result.getProcId());
  }

  private static void assertLockInfoEquals(LockInfo expected, LockInfo result)
  {
    assertEquals(expected.getResourceType(), result.getResourceType());
    assertEquals(expected.getResourceName(), result.getResourceName());
    assertEquals(expected.getLockType(), result.getLockType());
    assertProcedureInfoEquals(expected.getExclusiveLockOwnerProcedure(),
        result.getExclusiveLockOwnerProcedure());
    assertEquals(expected.getSharedLockCount(), result.getSharedLockCount());
  }

  private static void assertWaitingProcedureEquals(
      LockInfo.WaitingProcedure expected, LockInfo.WaitingProcedure result)
  {
    assertEquals(expected.getLockType(), result.getLockType());
    assertProcedureInfoEquals(expected.getProcedure(),
        result.getProcedure());
  }

  @Test
  public void testServerLockInfo() {
    LockInfo lock = new LockInfo();
    lock.setResourceType(LockInfo.ResourceType.SERVER);
    lock.setResourceName("server");
    lock.setLockType(LockInfo.LockType.SHARED);
    lock.setSharedLockCount(2);

    LockServiceProtos.LockInfo proto = ProtobufUtil.toProtoLockInfo(lock);
    LockInfo lock2 = ProtobufUtil.toLockInfo(proto);

    assertLockInfoEquals(lock, lock2);
  }

  @Test
  public void testNamespaceLockInfo() {
    LockInfo lock = new LockInfo();
    lock.setResourceType(LockInfo.ResourceType.NAMESPACE);
    lock.setResourceName("ns");
    lock.setLockType(LockInfo.LockType.EXCLUSIVE);
    lock.setExclusiveLockOwnerProcedure(createProcedureInfo(2));

    LockServiceProtos.LockInfo proto = ProtobufUtil.toProtoLockInfo(lock);
    LockInfo lock2 = ProtobufUtil.toLockInfo(proto);

    assertLockInfoEquals(lock, lock2);
  }

  @Test
  public void testTableLockInfo() {
    LockInfo lock = new LockInfo();
    lock.setResourceType(LockInfo.ResourceType.TABLE);
    lock.setResourceName("table");
    lock.setLockType(LockInfo.LockType.SHARED);
    lock.setSharedLockCount(2);

    LockServiceProtos.LockInfo proto = ProtobufUtil.toProtoLockInfo(lock);
    LockInfo lock2 = ProtobufUtil.toLockInfo(proto);

    assertLockInfoEquals(lock, lock2);
  }

  @Test
  public void testRegionLockInfo() {
    LockInfo lock = new LockInfo();
    lock.setResourceType(LockInfo.ResourceType.REGION);
    lock.setResourceName("region");
    lock.setLockType(LockInfo.LockType.EXCLUSIVE);
    lock.setExclusiveLockOwnerProcedure(createProcedureInfo(2));

    LockServiceProtos.LockInfo proto = ProtobufUtil.toProtoLockInfo(lock);
    LockInfo lock2 = ProtobufUtil.toLockInfo(proto);

    assertLockInfoEquals(lock, lock2);
  }

  @Test
  public void testExclusiveWaitingLockInfo() {
    LockInfo.WaitingProcedure waitingProcedure = new LockInfo.WaitingProcedure();
    waitingProcedure.setLockType(LockInfo.LockType.EXCLUSIVE);
    waitingProcedure.setProcedure(createProcedureInfo(1));

    LockServiceProtos.WaitingProcedure proto = ProtobufUtil.toProtoWaitingProcedure(waitingProcedure);
    LockInfo.WaitingProcedure waitingProcedure2 = ProtobufUtil.toWaitingProcedure(proto);

    assertWaitingProcedureEquals(waitingProcedure, waitingProcedure2);
  }

  @Test
  public void testSharedWaitingLockInfo() {
    LockInfo.WaitingProcedure waitingProcedure = new LockInfo.WaitingProcedure();
    waitingProcedure.setLockType(LockInfo.LockType.SHARED);
    waitingProcedure.setProcedure(createProcedureInfo(2));

    LockServiceProtos.WaitingProcedure proto = ProtobufUtil.toProtoWaitingProcedure(waitingProcedure);
    LockInfo.WaitingProcedure waitingProcedure2 = ProtobufUtil.toWaitingProcedure(proto);

    assertWaitingProcedureEquals(waitingProcedure, waitingProcedure2);
  }

  /**
   * Test Increment Mutate conversions.
   *
   * @throws IOException
   */
  @Test
  public void testIncrement() throws IOException {
    long timeStamp = 111111;
    MutationProto.Builder mutateBuilder = MutationProto.newBuilder();
    mutateBuilder.setRow(ByteString.copyFromUtf8("row"));
    mutateBuilder.setMutateType(MutationProto.MutationType.INCREMENT);
    ColumnValue.Builder valueBuilder = ColumnValue.newBuilder();
    valueBuilder.setFamily(ByteString.copyFromUtf8("f1"));
    QualifierValue.Builder qualifierBuilder = QualifierValue.newBuilder();
    qualifierBuilder.setQualifier(ByteString.copyFromUtf8("c1"));
    qualifierBuilder.setValue(ByteString.copyFrom(Bytes.toBytes(11L)));
    qualifierBuilder.setTimestamp(timeStamp);
    valueBuilder.addQualifierValue(qualifierBuilder.build());
    qualifierBuilder.setQualifier(ByteString.copyFromUtf8("c2"));
    qualifierBuilder.setValue(ByteString.copyFrom(Bytes.toBytes(22L)));
    valueBuilder.addQualifierValue(qualifierBuilder.build());
    mutateBuilder.addColumnValue(valueBuilder.build());

    MutationProto proto = mutateBuilder.build();
    // default fields
    assertEquals(MutationProto.Durability.USE_DEFAULT, proto.getDurability());

    // set the default value for equal comparison
    mutateBuilder = MutationProto.newBuilder(proto);
    mutateBuilder.setDurability(MutationProto.Durability.USE_DEFAULT);

    Increment increment = ProtobufUtil.toIncrement(proto, null);
    mutateBuilder.setTimestamp(increment.getTimeStamp());
    assertEquals(mutateBuilder.build(), ProtobufUtil.toMutation(MutationType.INCREMENT, increment));
  }

  /**
   * Test Append Mutate conversions.
   *
   * @throws IOException
   */
  @Test
  public void testAppend() throws IOException {
    long timeStamp = 111111;
    MutationProto.Builder mutateBuilder = MutationProto.newBuilder();
    mutateBuilder.setRow(ByteString.copyFromUtf8("row"));
    mutateBuilder.setMutateType(MutationType.APPEND);
    mutateBuilder.setTimestamp(timeStamp);
    ColumnValue.Builder valueBuilder = ColumnValue.newBuilder();
    valueBuilder.setFamily(ByteString.copyFromUtf8("f1"));
    QualifierValue.Builder qualifierBuilder = QualifierValue.newBuilder();
    qualifierBuilder.setQualifier(ByteString.copyFromUtf8("c1"));
    qualifierBuilder.setValue(ByteString.copyFromUtf8("v1"));
    qualifierBuilder.setTimestamp(timeStamp);
    valueBuilder.addQualifierValue(qualifierBuilder.build());
    qualifierBuilder.setQualifier(ByteString.copyFromUtf8("c2"));
    qualifierBuilder.setValue(ByteString.copyFromUtf8("v2"));
    valueBuilder.addQualifierValue(qualifierBuilder.build());
    mutateBuilder.addColumnValue(valueBuilder.build());

    MutationProto proto = mutateBuilder.build();
    // default fields
    assertEquals(MutationProto.Durability.USE_DEFAULT, proto.getDurability());

    // set the default value for equal comparison
    mutateBuilder = MutationProto.newBuilder(proto);
    mutateBuilder.setDurability(MutationProto.Durability.USE_DEFAULT);

    Append append = ProtobufUtil.toAppend(proto, null);

    // append always use the latest timestamp,
    // reset the timestamp to the original mutate
    mutateBuilder.setTimestamp(append.getTimeStamp());
    assertEquals(mutateBuilder.build(), ProtobufUtil.toMutation(MutationType.APPEND, append));
  }
}
