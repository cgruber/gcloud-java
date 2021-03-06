/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gcloud.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gcloud.AuthCredentials;
import com.google.gcloud.AuthCredentials.AppEngineAuthCredentials;
import com.google.gcloud.AuthCredentials.ServiceAccountAuthCredentials;
import com.google.gcloud.FieldSelector;
import com.google.gcloud.FieldSelector.Helper;
import com.google.gcloud.Page;
import com.google.gcloud.ReadChannel;
import com.google.gcloud.Service;
import com.google.gcloud.ServiceAccountSigner;
import com.google.gcloud.ServiceAccountSigner.SigningException;
import com.google.gcloud.WriteChannel;
import com.google.gcloud.storage.spi.StorageRpc;
import com.google.gcloud.storage.spi.StorageRpc.Tuple;

import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * An interface for Google Cloud Storage.
 *
 * @see <a href="https://cloud.google.com/storage/docs">Google Cloud Storage</a>
 */
public interface Storage extends Service<StorageOptions> {

  enum PredefinedAcl {
    AUTHENTICATED_READ("authenticatedRead"),
    ALL_AUTHENTICATED_USERS("allAuthenticatedUsers"),
    PRIVATE("private"),
    PROJECT_PRIVATE("projectPrivate"),
    PUBLIC_READ("publicRead"),
    PUBLIC_READ_WRITE("publicReadWrite"),
    BUCKET_OWNER_READ("bucketOwnerRead"),
    BUCKET_OWNER_FULL_CONTROL("bucketOwnerFullControl");

    private final String entry;

    PredefinedAcl(String entry) {
      this.entry = entry;
    }

    String entry() {
      return entry;
    }
  }

  enum BucketField implements FieldSelector {
    ID("id"),
    SELF_LINK("selfLink"),
    NAME("name"),
    TIME_CREATED("timeCreated"),
    METAGENERATION("metageneration"),
    ACL("acl"),
    DEFAULT_OBJECT_ACL("defaultObjectAcl"),
    OWNER("owner"),
    LOCATION("location"),
    WEBSITE("website"),
    VERSIONING("versioning"),
    CORS("cors"),
    STORAGE_CLASS("storageClass"),
    ETAG("etag");

    static final List<? extends FieldSelector> REQUIRED_FIELDS = ImmutableList.of(NAME);

    private final String selector;

    BucketField(String selector) {
      this.selector = selector;
    }

    @Override
    public String selector() {
      return selector;
    }
  }

  enum BlobField implements FieldSelector {
    ACL("acl"),
    BUCKET("bucket"),
    CACHE_CONTROL("cacheControl"),
    COMPONENT_COUNT("componentCount"),
    CONTENT_DISPOSITION("contentDisposition"),
    CONTENT_ENCODING("contentEncoding"),
    CONTENT_LANGUAGE("contentLanguage"),
    CONTENT_TYPE("contentType"),
    CRC32C("crc32c"),
    ETAG("etag"),
    GENERATION("generation"),
    ID("id"),
    KIND("kind"),
    MD5HASH("md5Hash"),
    MEDIA_LINK("mediaLink"),
    METADATA("metadata"),
    METAGENERATION("metageneration"),
    NAME("name"),
    OWNER("owner"),
    SELF_LINK("selfLink"),
    SIZE("size"),
    STORAGE_CLASS("storageClass"),
    TIME_DELETED("timeDeleted"),
    UPDATED("updated");

    static final List<? extends FieldSelector> REQUIRED_FIELDS = ImmutableList.of(BUCKET, NAME);

    private final String selector;

    BlobField(String selector) {
      this.selector = selector;
    }

    @Override
    public String selector() {
      return selector;
    }
  }

  /**
   * Class for specifying bucket target options.
   */
  class BucketTargetOption extends Option {

    private static final long serialVersionUID = -5880204616982900975L;

    private BucketTargetOption(StorageRpc.Option rpcOption, Object value) {
      super(rpcOption, value);
    }

    private BucketTargetOption(StorageRpc.Option rpcOption) {
      this(rpcOption, null);
    }

    /**
     * Returns an option for specifying bucket's predefined ACL configuration.
     */
    public static BucketTargetOption predefinedAcl(PredefinedAcl acl) {
      return new BucketTargetOption(StorageRpc.Option.PREDEFINED_ACL, acl.entry());
    }

    /**
     * Returns an option for specifying bucket's default ACL configuration for blobs.
     */
    public static BucketTargetOption predefinedDefaultObjectAcl(PredefinedAcl acl) {
      return new BucketTargetOption(StorageRpc.Option.PREDEFINED_DEFAULT_OBJECT_ACL, acl.entry());
    }

    /**
     * Returns an option for bucket's metageneration match. If this option is used the request will
     * fail if metageneration does not match.
     */
    public static BucketTargetOption metagenerationMatch() {
      return new BucketTargetOption(StorageRpc.Option.IF_METAGENERATION_MATCH);
    }

    /**
     * Returns an option for bucket's metageneration mismatch. If this option is used the request
     * will fail if metageneration matches.
     */
    public static BucketTargetOption metagenerationNotMatch() {
      return new BucketTargetOption(StorageRpc.Option.IF_METAGENERATION_NOT_MATCH);
    }
  }

  /**
   * Class for specifying bucket source options.
   */
  class BucketSourceOption extends Option {

    private static final long serialVersionUID = 5185657617120212117L;

    private BucketSourceOption(StorageRpc.Option rpcOption, long metageneration) {
      super(rpcOption, metageneration);
    }

    /**
     * Returns an option for bucket's metageneration match. If this option is used the request will
     * fail if bucket's metageneration does not match the provided value.
     */
    public static BucketSourceOption metagenerationMatch(long metageneration) {
      return new BucketSourceOption(StorageRpc.Option.IF_METAGENERATION_MATCH, metageneration);
    }

    /**
     * Returns an option for bucket's metageneration mismatch. If this option is used the request
     * will fail if bucket's metageneration matches the provided value.
     */
    public static BucketSourceOption metagenerationNotMatch(long metageneration) {
      return new BucketSourceOption(StorageRpc.Option.IF_METAGENERATION_NOT_MATCH, metageneration);
    }
  }

  /**
   * Class for specifying bucket source options.
   */
  class BucketGetOption extends Option {

    private static final long serialVersionUID = 1901844869484087395L;

    private BucketGetOption(StorageRpc.Option rpcOption, long metageneration) {
      super(rpcOption, metageneration);
    }

    private BucketGetOption(StorageRpc.Option rpcOption, String value) {
      super(rpcOption, value);
    }

    /**
     * Returns an option for bucket's metageneration match. If this option is used the request will
     * fail if bucket's metageneration does not match the provided value.
     */
    public static BucketGetOption metagenerationMatch(long metageneration) {
      return new BucketGetOption(StorageRpc.Option.IF_METAGENERATION_MATCH, metageneration);
    }

    /**
     * Returns an option for bucket's metageneration mismatch. If this option is used the request
     * will fail if bucket's metageneration matches the provided value.
     */
    public static BucketGetOption metagenerationNotMatch(long metageneration) {
      return new BucketGetOption(StorageRpc.Option.IF_METAGENERATION_NOT_MATCH, metageneration);
    }

    /**
     * Returns an option to specify the bucket's fields to be returned by the RPC call. If this
     * option is not provided all bucket's fields are returned. {@code BucketGetOption.fields}) can
     * be used to specify only the fields of interest. Bucket name is always returned, even if not
     * specified.
     */
    public static BucketGetOption fields(BucketField... fields) {
      return new BucketGetOption(StorageRpc.Option.FIELDS,
          Helper.selector(BucketField.REQUIRED_FIELDS, fields));
    }
  }

  /**
   * Class for specifying blob target options.
   */
  class BlobTargetOption extends Option {

    private static final long serialVersionUID = 214616862061934846L;

    private BlobTargetOption(StorageRpc.Option rpcOption, Object value) {
      super(rpcOption, value);
    }

    private BlobTargetOption(StorageRpc.Option rpcOption) {
      this(rpcOption, null);
    }

    /**
     * Returns an option for specifying blob's predefined ACL configuration.
     */
    public static BlobTargetOption predefinedAcl(PredefinedAcl acl) {
      return new BlobTargetOption(StorageRpc.Option.PREDEFINED_ACL, acl.entry());
    }

    /**
     * Returns an option that causes an operation to succeed only if the target blob does not exist.
     */
    public static BlobTargetOption doesNotExist() {
      return new BlobTargetOption(StorageRpc.Option.IF_GENERATION_MATCH, 0L);
    }

    /**
     * Returns an option for blob's data generation match. If this option is used the request will
     * fail if generation does not match.
     */
    public static BlobTargetOption generationMatch() {
      return new BlobTargetOption(StorageRpc.Option.IF_GENERATION_MATCH);
    }

    /**
     * Returns an option for blob's data generation mismatch. If this option is used the request
     * will fail if generation matches.
     */
    public static BlobTargetOption generationNotMatch() {
      return new BlobTargetOption(StorageRpc.Option.IF_GENERATION_NOT_MATCH);
    }

    /**
     * Returns an option for blob's metageneration match. If this option is used the request will
     * fail if metageneration does not match.
     */
    public static BlobTargetOption metagenerationMatch() {
      return new BlobTargetOption(StorageRpc.Option.IF_METAGENERATION_MATCH);
    }

    /**
     * Returns an option for blob's metageneration mismatch. If this option is used the request will
     * fail if metageneration matches.
     */
    public static BlobTargetOption metagenerationNotMatch() {
      return new BlobTargetOption(StorageRpc.Option.IF_METAGENERATION_NOT_MATCH);
    }

    static Tuple<BlobInfo, BlobTargetOption[]> convert(BlobInfo info, BlobWriteOption... options) {
      BlobInfo.Builder infoBuilder = info.toBuilder().crc32c(null).md5(null);
      List<BlobTargetOption> targetOptions = Lists.newArrayListWithCapacity(options.length);
      for (BlobWriteOption option : options) {
        switch (option.option) {
          case IF_CRC32C_MATCH:
            infoBuilder.crc32c(info.crc32c());
            break;
          case IF_MD5_MATCH:
            infoBuilder.md5(info.md5());
            break;
          default:
            targetOptions.add(option.toTargetOption());
            break;
        }
      }
      return Tuple.of(infoBuilder.build(),
          targetOptions.toArray(new BlobTargetOption[targetOptions.size()]));
    }
  }

  /**
   * Class for specifying blob write options.
   */
  class BlobWriteOption implements Serializable {

    private static final long serialVersionUID = -3880421670966224580L;

    private final Option option;
    private final Object value;

    enum Option {
      PREDEFINED_ACL, IF_GENERATION_MATCH, IF_GENERATION_NOT_MATCH, IF_METAGENERATION_MATCH,
      IF_METAGENERATION_NOT_MATCH, IF_MD5_MATCH, IF_CRC32C_MATCH;

      StorageRpc.Option toRpcOption() {
        return StorageRpc.Option.valueOf(this.name());
      }
    }

    BlobTargetOption toTargetOption() {
      return new BlobTargetOption(this.option.toRpcOption(), this.value);
    }

    private BlobWriteOption(Option option, Object value) {
      this.option = option;
      this.value = value;
    }

    private BlobWriteOption(Option option) {
      this(option, null);
    }

    @Override
    public int hashCode() {
      return Objects.hash(option, value);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (!(obj instanceof BlobWriteOption)) {
        return false;
      }
      final BlobWriteOption other = (BlobWriteOption) obj;
      return this.option == other.option && Objects.equals(this.value, other.value);
    }

    /**
     * Returns an option for specifying blob's predefined ACL configuration.
     */
    public static BlobWriteOption predefinedAcl(PredefinedAcl acl) {
      return new BlobWriteOption(Option.PREDEFINED_ACL, acl.entry());
    }

    /**
     * Returns an option that causes an operation to succeed only if the target blob does not exist.
     */
    public static BlobWriteOption doesNotExist() {
      return new BlobWriteOption(Option.IF_GENERATION_MATCH, 0L);
    }

    /**
     * Returns an option for blob's data generation match. If this option is used the request will
     * fail if generation does not match.
     */
    public static BlobWriteOption generationMatch() {
      return new BlobWriteOption(Option.IF_GENERATION_MATCH);
    }

    /**
     * Returns an option for blob's data generation mismatch. If this option is used the request
     * will fail if generation matches.
     */
    public static BlobWriteOption generationNotMatch() {
      return new BlobWriteOption(Option.IF_GENERATION_NOT_MATCH);
    }

    /**
     * Returns an option for blob's metageneration match. If this option is used the request will
     * fail if metageneration does not match.
     */
    public static BlobWriteOption metagenerationMatch() {
      return new BlobWriteOption(Option.IF_METAGENERATION_MATCH);
    }

    /**
     * Returns an option for blob's metageneration mismatch. If this option is used the request will
     * fail if metageneration matches.
     */
    public static BlobWriteOption metagenerationNotMatch() {
      return new BlobWriteOption(Option.IF_METAGENERATION_NOT_MATCH);
    }

    /**
     * Returns an option for blob's data MD5 hash match. If this option is used the request will
     * fail if blobs' data MD5 hash does not match.
     */
    public static BlobWriteOption md5Match() {
      return new BlobWriteOption(Option.IF_MD5_MATCH, true);
    }

    /**
     * Returns an option for blob's data CRC32C checksum match. If this option is used the request
     * will fail if blobs' data CRC32C checksum does not match.
     */
    public static BlobWriteOption crc32cMatch() {
      return new BlobWriteOption(Option.IF_CRC32C_MATCH, true);
    }
  }

  /**
   * Class for specifying blob source options.
   */
  class BlobSourceOption extends Option {

    private static final long serialVersionUID = -3712768261070182991L;

    private BlobSourceOption(StorageRpc.Option rpcOption, Long value) {
      super(rpcOption, value);
    }

    /**
     * Returns an option for blob's data generation match. If this option is used the request will
     * fail if blob's generation does not match. The generation value to compare with the actual
     * blob's generation is taken from a source {@link BlobId} object. When this option is passed
     * to a {@link Storage} method and {@link BlobId#generation()} is {@code null} or no
     * {@link BlobId} is provided an exception is thrown.
     */
    public static BlobSourceOption generationMatch() {
      return new BlobSourceOption(StorageRpc.Option.IF_GENERATION_MATCH, null);
    }

    /**
     * Returns an option for blob's data generation match. If this option is used the request will
     * fail if blob's generation does not match the provided value.
     */
    public static BlobSourceOption generationMatch(long generation) {
      return new BlobSourceOption(StorageRpc.Option.IF_GENERATION_MATCH, generation);
    }

    /**
     * Returns an option for blob's data generation mismatch. If this option is used the request
     * will fail if blob's generation matches. The generation value to compare with the actual
     * blob's generation is taken from a source {@link BlobId} object. When this option is passed
     * to a {@link Storage} method and {@link BlobId#generation()} is {@code null} or no
     * {@link BlobId} is provided an exception is thrown.
     */
    public static BlobSourceOption generationNotMatch() {
      return new BlobSourceOption(StorageRpc.Option.IF_GENERATION_NOT_MATCH, null);
    }

    /**
     * Returns an option for blob's data generation mismatch. If this option is used the request
     * will fail if blob's generation matches the provided value.
     */
    public static BlobSourceOption generationNotMatch(long generation) {
      return new BlobSourceOption(StorageRpc.Option.IF_GENERATION_NOT_MATCH, generation);
    }

    /**
     * Returns an option for blob's metageneration match. If this option is used the request will
     * fail if blob's metageneration does not match the provided value.
     */
    public static BlobSourceOption metagenerationMatch(long metageneration) {
      return new BlobSourceOption(StorageRpc.Option.IF_METAGENERATION_MATCH, metageneration);
    }

    /**
     * Returns an option for blob's metageneration mismatch. If this option is used the request will
     * fail if blob's metageneration matches the provided value.
     */
    public static BlobSourceOption metagenerationNotMatch(long metageneration) {
      return new BlobSourceOption(StorageRpc.Option.IF_METAGENERATION_NOT_MATCH, metageneration);
    }
  }

  /**
   * Class for specifying blob get options.
   */
  class BlobGetOption extends Option {

    private static final long serialVersionUID = 803817709703661480L;

    private BlobGetOption(StorageRpc.Option rpcOption, Long value) {
      super(rpcOption, value);
    }

    private BlobGetOption(StorageRpc.Option rpcOption, String value) {
      super(rpcOption, value);
    }

    /**
     * Returns an option for blob's data generation match. If this option is used the request will
     * fail if blob's generation does not match. The generation value to compare with the actual
     * blob's generation is taken from a source {@link BlobId} object. When this option is passed
     * to a {@link Storage} method and {@link BlobId#generation()} is {@code null} or no
     * {@link BlobId} is provided an exception is thrown.
     */
    public static BlobGetOption generationMatch() {
      return new BlobGetOption(StorageRpc.Option.IF_GENERATION_MATCH, (Long) null);
    }

    /**
     * Returns an option for blob's data generation match. If this option is used the request will
     * fail if blob's generation does not match the provided value.
     */
    public static BlobGetOption generationMatch(long generation) {
      return new BlobGetOption(StorageRpc.Option.IF_GENERATION_MATCH, generation);
    }

    /**
     * Returns an option for blob's data generation mismatch. If this option is used the request
     * will fail if blob's generation matches. The generation value to compare with the actual
     * blob's generation is taken from a source {@link BlobId} object. When this option is passed
     * to a {@link Storage} method and {@link BlobId#generation()} is {@code null} or no
     * {@link BlobId} is provided an exception is thrown.
     */
    public static BlobGetOption generationNotMatch() {
      return new BlobGetOption(StorageRpc.Option.IF_GENERATION_NOT_MATCH, (Long) null);
    }

    /**
     * Returns an option for blob's data generation mismatch. If this option is used the request
     * will fail if blob's generation matches the provided value.
     */
    public static BlobGetOption generationNotMatch(long generation) {
      return new BlobGetOption(StorageRpc.Option.IF_GENERATION_NOT_MATCH, generation);
    }

    /**
     * Returns an option for blob's metageneration match. If this option is used the request will
     * fail if blob's metageneration does not match the provided value.
     */
    public static BlobGetOption metagenerationMatch(long metageneration) {
      return new BlobGetOption(StorageRpc.Option.IF_METAGENERATION_MATCH, metageneration);
    }

    /**
     * Returns an option for blob's metageneration mismatch. If this option is used the request will
     * fail if blob's metageneration matches the provided value.
     */
    public static BlobGetOption metagenerationNotMatch(long metageneration) {
      return new BlobGetOption(StorageRpc.Option.IF_METAGENERATION_NOT_MATCH, metageneration);
    }

    /**
     * Returns an option to specify the blob's fields to be returned by the RPC call. If this option
     * is not provided all blob's fields are returned. {@code BlobGetOption.fields}) can be used to
     * specify only the fields of interest. Blob name and bucket are always returned, even if not
     * specified.
     */
    public static BlobGetOption fields(BlobField... fields) {
      return new BlobGetOption(StorageRpc.Option.FIELDS,
          Helper.selector(BlobField.REQUIRED_FIELDS, fields));
    }
  }

  /**
   * Class for specifying bucket list options.
   */
  class BucketListOption extends Option {

    private static final long serialVersionUID = 8754017079673290353L;

    private BucketListOption(StorageRpc.Option option, Object value) {
      super(option, value);
    }

    /**
     * Returns an option to specify the maximum number of buckets returned per page.
     */
    public static BucketListOption pageSize(long pageSize) {
      return new BucketListOption(StorageRpc.Option.MAX_RESULTS, pageSize);
    }

    /**
     * Returns an option to specify the page token from which to start listing buckets.
     */
    public static BucketListOption pageToken(String pageToken) {
      return new BucketListOption(StorageRpc.Option.PAGE_TOKEN, pageToken);
    }

    /**
     * Returns an option to set a prefix to filter results to buckets whose names begin with this
     * prefix.
     */
    public static BucketListOption prefix(String prefix) {
      return new BucketListOption(StorageRpc.Option.PREFIX, prefix);
    }

    /**
     * Returns an option to specify the bucket's fields to be returned by the RPC call. If this
     * option is not provided all bucket's fields are returned. {@code BucketListOption.fields}) can
     * be used to specify only the fields of interest. Bucket name is always returned, even if not
     * specified.
     */
    public static BucketListOption fields(BucketField... fields) {
      return new BucketListOption(StorageRpc.Option.FIELDS,
          Helper.listSelector("items", BucketField.REQUIRED_FIELDS, fields));
    }
  }

  /**
   * Class for specifying blob list options.
   */
  class BlobListOption extends Option {

    private static final long serialVersionUID = 9083383524788661294L;

    private BlobListOption(StorageRpc.Option option, Object value) {
      super(option, value);
    }

    /**
     * Returns an option to specify the maximum number of blobs returned per page.
     */
    public static BlobListOption pageSize(long pageSize) {
      return new BlobListOption(StorageRpc.Option.MAX_RESULTS, pageSize);
    }

    /**
     * Returns an option to specify the page token from which to start listing blobs.
     */
    public static BlobListOption pageToken(String pageToken) {
      return new BlobListOption(StorageRpc.Option.PAGE_TOKEN, pageToken);
    }

    /**
     * Returns an option to set a prefix to filter results to blobs whose names begin with this
     * prefix.
     */
    public static BlobListOption prefix(String prefix) {
      return new BlobListOption(StorageRpc.Option.PREFIX, prefix);
    }

    /**
     * If specified, results are returned in a directory-like mode. Blobs whose names, after a
     * possible {@link #prefix(String)}, do not contain the '/' delimiter are returned as is. Blobs
     * whose names, after a possible {@link #prefix(String)}, contain the '/' delimiter, will have
     * their name truncated after the delimiter and will be returned as {@link Blob} objects where
     * only {@link Blob#blobId()}, {@link Blob#size()} and {@link Blob#isDirectory()} are set. For
     * such directory blobs, ({@link BlobId#generation()} returns {@code null}), {@link Blob#size()}
     * returns {@code 0} while {@link Blob#isDirectory()} returns {@code true}. Duplicate directory
     * blobs are omitted.
     */
    public static BlobListOption currentDirectory() {
      return new BlobListOption(StorageRpc.Option.DELIMITER, true);
    }

    /**
     * If set to {@code true}, lists all versions of a blob. The default is {@code false}.
     *
     * @see <a href ="https://cloud.google.com/storage/docs/object-versioning">Object Versioning</a>
     */
    public static BlobListOption versions(boolean versions) {
      return new BlobListOption(StorageRpc.Option.VERSIONS, versions);
    }

    /**
     * Returns an option to specify the blob's fields to be returned by the RPC call. If this option
     * is not provided all blob's fields are returned. {@code BlobListOption.fields}) can be used to
     * specify only the fields of interest. Blob name and bucket are always returned, even if not
     * specified.
     */
    public static BlobListOption fields(BlobField... fields) {
      return new BlobListOption(StorageRpc.Option.FIELDS,
          Helper.listSelector("items", BlobField.REQUIRED_FIELDS, fields));
    }
  }

  /**
   * Class for specifying signed URL options.
   */
  class SignUrlOption implements Serializable {

    private static final long serialVersionUID = 7850569877451099267L;

    private final Option option;
    private final Object value;

    enum Option {
      HTTP_METHOD, CONTENT_TYPE, MD5, SERVICE_ACCOUNT_CRED
    }

    private SignUrlOption(Option option, Object value) {
      this.option = option;
      this.value = value;
    }

    Option option() {
      return option;
    }

    Object value() {
      return value;
    }

    /**
     * The HTTP method to be used with the signed URL.
     */
    public static SignUrlOption httpMethod(HttpMethod httpMethod) {
      return new SignUrlOption(Option.HTTP_METHOD, httpMethod.name());
    }

    /**
     * Use it if signature should include the blob's content-type.
     * When used, users of the signed URL should include the blob's content-type with their request.
     */
    public static SignUrlOption withContentType() {
      return new SignUrlOption(Option.CONTENT_TYPE, true);
    }

    /**
     * Use it if signature should include the blob's md5.
     * When used, users of the signed URL should include the blob's md5 with their request.
     */
    public static SignUrlOption withMd5() {
      return new SignUrlOption(Option.MD5, true);
    }

    /**
     * Provides a service account signer to sign the URL. If not provided an attempt will be made to
     * get it from the environment.
     *
     * @see <a href="https://cloud.google.com/storage/docs/authentication#service_accounts">Service
     *     account</a>
     */
    public static SignUrlOption signWith(ServiceAccountSigner signer) {
      return new SignUrlOption(Option.SERVICE_ACCOUNT_CRED, signer);
    }
  }

  /**
   * A class to contain all information needed for a Google Cloud Storage Compose operation.
   *
   * @see <a href="https://cloud.google.com/storage/docs/composite-objects#_Compose">
   *     Compose Operation</a>
   */
  class ComposeRequest implements Serializable {

    private static final long serialVersionUID = -7385681353748590911L;

    private final List<SourceBlob> sourceBlobs;
    private final BlobInfo target;
    private final List<BlobTargetOption> targetOptions;

    /**
     * Class for Compose source blobs.
     */
    public static class SourceBlob implements Serializable {

      private static final long serialVersionUID = 4094962795951990439L;

      final String name;
      final Long generation;

      SourceBlob(String name) {
        this(name, null);
      }

      SourceBlob(String name, Long generation) {
        this.name = name;
        this.generation = generation;
      }

      public String name() {
        return name;
      }

      public Long generation() {
        return generation;
      }
    }

    public static class Builder {

      private final List<SourceBlob> sourceBlobs = new LinkedList<>();
      private final Set<BlobTargetOption> targetOptions = new LinkedHashSet<>();
      private BlobInfo target;

      /**
       * Add source blobs for compose operation.
       */
      public Builder addSource(Iterable<String> blobs) {
        for (String blob : blobs) {
          sourceBlobs.add(new SourceBlob(blob));
        }
        return this;
      }

      /**
       * Add source blobs for compose operation.
       */
      public Builder addSource(String... blobs) {
        return addSource(Arrays.asList(blobs));
      }

      /**
       * Add a source with a specific generation to match.
       */
      public Builder addSource(String blob, long generation) {
        sourceBlobs.add(new SourceBlob(blob, generation));
        return this;
      }

      /**
       * Sets compose operation's target blob.
       */
      public Builder target(BlobInfo target) {
        this.target = target;
        return this;
      }

      /**
       * Sets compose operation's target blob options.
       */
      public Builder targetOptions(BlobTargetOption... options) {
        Collections.addAll(targetOptions, options);
        return this;
      }

      /**
       * Sets compose operation's target blob options.
       */
      public Builder targetOptions(Iterable<BlobTargetOption> options) {
        Iterables.addAll(targetOptions, options);
        return this;
      }

      /**
       * Creates a {@code ComposeRequest} object.
       */
      public ComposeRequest build() {
        checkArgument(!sourceBlobs.isEmpty());
        checkNotNull(target);
        return new ComposeRequest(this);
      }
    }

    private ComposeRequest(Builder builder) {
      sourceBlobs = ImmutableList.copyOf(builder.sourceBlobs);
      target = builder.target;
      targetOptions = ImmutableList.copyOf(builder.targetOptions);
    }

    /**
     * Returns compose operation's source blobs.
     */
    public List<SourceBlob> sourceBlobs() {
      return sourceBlobs;
    }

    /**
     * Returns compose operation's target blob.
     */
    public BlobInfo target() {
      return target;
    }

    /**
     * Returns compose operation's target blob's options.
     */
    public List<BlobTargetOption> targetOptions() {
      return targetOptions;
    }

    /**
     * Creates a {@code ComposeRequest} object.
     *
     * @param sources source blobs names
     * @param target target blob
     */
    public static ComposeRequest of(Iterable<String> sources, BlobInfo target) {
      return builder().target(target).addSource(sources).build();
    }

    /**
     * Creates a {@code ComposeRequest} object.
     *
     * @param bucket name of the bucket where the compose operation takes place
     * @param sources source blobs names
     * @param target target blob name
     */
    public static ComposeRequest of(String bucket, Iterable<String> sources, String target) {
      return of(sources, BlobInfo.builder(BlobId.of(bucket, target)).build());
    }

    /**
     * Returns a {@code ComposeRequest} builder.
     */
    public static Builder builder() {
      return new Builder();
    }
  }

  /**
   * A class to contain all information needed for a Google Cloud Storage Copy operation.
   */
  class CopyRequest implements Serializable {

    private static final long serialVersionUID = -4498650529476219937L;

    private final BlobId source;
    private final List<BlobSourceOption> sourceOptions;
    private final boolean overrideInfo;
    private final BlobInfo target;
    private final List<BlobTargetOption> targetOptions;
    private final Long megabytesCopiedPerChunk;

    public static class Builder {

      private final Set<BlobSourceOption> sourceOptions = new LinkedHashSet<>();
      private final Set<BlobTargetOption> targetOptions = new LinkedHashSet<>();
      private BlobId source;
      private boolean overrideInfo;
      private BlobInfo target;
      private Long megabytesCopiedPerChunk;

      /**
       * Sets the blob to copy given bucket and blob name.
       *
       * @return the builder
       */
      public Builder source(String bucket, String blob) {
        this.source = BlobId.of(bucket, blob);
        return this;
      }

      /**
       * Sets the blob to copy given a {@link BlobId}.
       *
       * @return the builder
       */
      public Builder source(BlobId source) {
        this.source = source;
        return this;
      }

      /**
       * Sets blob's source options.
       *
       * @return the builder
       */
      public Builder sourceOptions(BlobSourceOption... options) {
        Collections.addAll(sourceOptions, options);
        return this;
      }

      /**
       * Sets blob's source options.
       *
       * @return the builder
       */
      public Builder sourceOptions(Iterable<BlobSourceOption> options) {
        Iterables.addAll(sourceOptions, options);
        return this;
      }

      /**
       * Sets the copy target. Target blob information is copied from source.
       *
       * @return the builder
       */
      public Builder target(BlobId targetId) {
        this.overrideInfo = false;
        this.target = BlobInfo.builder(targetId).build();
        return this;
      }

      /**
       * Sets the copy target and target options. {@code target} parameter is used to override
       * source blob information (e.g. {@code contentType}, {@code contentLanguage}). Target blob
       * information is set exactly to {@code target}, no information is inherited from the source
       * blob.
       *
       * @return the builder
       */
      public Builder target(BlobInfo target, BlobTargetOption... options) {
        this.overrideInfo = true;
        this.target = checkNotNull(target);
        Collections.addAll(targetOptions, options);
        return this;
      }

      /**
       * Sets the copy target and target options. {@code target} parameter is used to override
       * source blob information (e.g. {@code contentType}, {@code contentLanguage}). Target blob
       * information is set exactly to {@code target}, no information is inherited from the source
       * blob.
       *
       * @return the builder
       */
      public Builder target(BlobInfo target, Iterable<BlobTargetOption> options) {
        this.overrideInfo = true;
        this.target = checkNotNull(target);
        Iterables.addAll(targetOptions, options);
        return this;
      }

      /**
       * Sets the maximum number of megabytes to copy for each RPC call. This parameter is ignored
       * if source and target blob share the same location and storage class as copy is made with
       * one single RPC.
       *
       * @return the builder
       */
      public Builder megabytesCopiedPerChunk(Long megabytesCopiedPerChunk) {
        this.megabytesCopiedPerChunk = megabytesCopiedPerChunk;
        return this;
      }

      /**
       * Creates a {@code CopyRequest} object.
       */
      public CopyRequest build() {
        return new CopyRequest(this);
      }
    }

    private CopyRequest(Builder builder) {
      source = checkNotNull(builder.source);
      sourceOptions = ImmutableList.copyOf(builder.sourceOptions);
      overrideInfo = builder.overrideInfo;
      target = checkNotNull(builder.target);
      targetOptions = ImmutableList.copyOf(builder.targetOptions);
      megabytesCopiedPerChunk = builder.megabytesCopiedPerChunk;
    }

    /**
     * Returns the blob to copy, as a {@link BlobId}.
     */
    public BlobId source() {
      return source;
    }

    /**
     * Returns blob's source options.
     */
    public List<BlobSourceOption> sourceOptions() {
      return sourceOptions;
    }

    /**
     * Returns the {@link BlobInfo} for the target blob.
     */
    public BlobInfo target() {
      return target;
    }

    /**
     * Returns whether to override the target blob information with {@link #target()}.
     * If {@code true}, the value of {@link #target()} is used to replace source blob information
     * (e.g. {@code contentType}, {@code contentLanguage}). Target blob information is set exactly
     * to this value, no information is inherited from the source blob. If {@code false}, target
     * blob information is inherited from the source blob.
     */
    public boolean overrideInfo() {
      return overrideInfo;
    }

    /**
     * Returns blob's target options.
     */
    public List<BlobTargetOption> targetOptions() {
      return targetOptions;
    }

    /**
     * Returns the maximum number of megabytes to copy for each RPC call. This parameter is ignored
     * if source and target blob share the same location and storage class as copy is made with
     * one single RPC.
     */
    public Long megabytesCopiedPerChunk() {
      return megabytesCopiedPerChunk;
    }

    /**
     * Creates a copy request. {@code target} parameter is used to override source blob information
     * (e.g. {@code contentType}, {@code contentLanguage}).
     *
     * @param sourceBucket name of the bucket containing the source blob
     * @param sourceBlob name of the source blob
     * @param target a {@code BlobInfo} object for the target blob
     * @return a copy request
     */
    public static CopyRequest of(String sourceBucket, String sourceBlob, BlobInfo target) {
      return builder().source(sourceBucket, sourceBlob).target(target).build();
    }

    /**
     * Creates a copy request. {@code target} parameter is used to replace source blob information
     * (e.g. {@code contentType}, {@code contentLanguage}). Target blob information is set exactly
     * to {@code target}, no information is inherited from the source blob.
     *
     * @param sourceBlobId a {@code BlobId} object for the source blob
     * @param target a {@code BlobInfo} object for the target blob
     * @return a copy request
     */
    public static CopyRequest of(BlobId sourceBlobId, BlobInfo target) {
      return builder().source(sourceBlobId).target(target).build();
    }

    /**
     * Creates a copy request. Target blob information is copied from source.
     *
     * @param sourceBucket name of the bucket containing both the source and the target blob
     * @param sourceBlob name of the source blob
     * @param targetBlob name of the target blob
     * @return a copy request
     */
    public static CopyRequest of(String sourceBucket, String sourceBlob, String targetBlob) {
      return CopyRequest.builder()
          .source(sourceBucket, sourceBlob)
          .target(BlobId.of(sourceBucket, targetBlob))
          .build();
    }

    /**
     * Creates a copy request. Target blob information is copied from source.
     *
     * @param sourceBucket name of the bucket containing the source blob
     * @param sourceBlob name of the source blob
     * @param target a {@code BlobId} object for the target blob
     * @return a copy request
     */
    public static CopyRequest of(String sourceBucket, String sourceBlob, BlobId target) {
      return builder().source(sourceBucket, sourceBlob).target(target).build();
    }

    /**
     * Creates a copy request. Target blob information is copied from source.
     *
     * @param sourceBlobId a {@code BlobId} object for the source blob
     * @param targetBlob name of the target blob, in the same bucket of the source blob
     * @return a copy request
     */
    public static CopyRequest of(BlobId sourceBlobId, String targetBlob) {
      return CopyRequest.builder()
          .source(sourceBlobId)
          .target(BlobId.of(sourceBlobId.bucket(), targetBlob))
          .build();
    }

    /**
     * Creates a copy request. Target blob information is copied from source.
     *
     * @param sourceBlobId a {@code BlobId} object for the source blob
     * @param targetBlobId a {@code BlobId} object for the target blob
     * @return a copy request
     */
    public static CopyRequest of(BlobId sourceBlobId, BlobId targetBlobId) {
      return CopyRequest.builder()
          .source(sourceBlobId)
          .target(targetBlobId)
          .build();
    }

    public static Builder builder() {
      return new Builder();
    }
  }

  /**
   * Creates a new bucket.
   *
   * @return a complete bucket
   * @throws StorageException upon failure
   */
  Bucket create(BucketInfo bucketInfo, BucketTargetOption... options);

  /**
   * Creates a new blob with no content.
   *
   * @return a [@code Blob} with complete information
   * @throws StorageException upon failure
   */
  Blob create(BlobInfo blobInfo, BlobTargetOption... options);

  /**
   * Creates a new blob. Direct upload is used to upload {@code content}. For large content,
   * {@link #writer} is recommended as it uses resumable upload. MD5 and CRC32C hashes of
   * {@code content} are computed and used for validating transferred data.
   *
   * @return a [@code Blob} with complete information
   * @throws StorageException upon failure
   * @see <a href="https://cloud.google.com/storage/docs/hashes-etags">Hashes and ETags</a>
   */
  Blob create(BlobInfo blobInfo, byte[] content, BlobTargetOption... options);

  /**
   * Creates a new blob. Direct upload is used to upload {@code content}. For large content,
   * {@link #writer} is recommended as it uses resumable upload. By default any md5 and crc32c
   * values in the given {@code blobInfo} are ignored unless requested via the
   * {@code BlobWriteOption.md5Match} and {@code BlobWriteOption.crc32cMatch} options. The given
   * input stream is closed upon success.
   *
   * @return a [@code Blob} with complete information
   * @throws StorageException upon failure
   */
  Blob create(BlobInfo blobInfo, InputStream content, BlobWriteOption... options);

  /**
   * Returns the requested bucket or {@code null} if not found.
   *
   * @throws StorageException upon failure
   */
  Bucket get(String bucket, BucketGetOption... options);

  /**
   * Returns the requested blob or {@code null} if not found.
   *
   * @throws StorageException upon failure
   */
  Blob get(String bucket, String blob, BlobGetOption... options);

  /**
   * Returns the requested blob or {@code null} if not found.
   *
   * @throws StorageException upon failure
   */
  Blob get(BlobId blob, BlobGetOption... options);

  /**
   * Returns the requested blob or {@code null} if not found.
   *
   * @throws StorageException upon failure
   */
  Blob get(BlobId blob);

  /**
   * Lists the project's buckets.
   *
   * @throws StorageException upon failure
   */
  Page<Bucket> list(BucketListOption... options);

  /**
   * Lists the bucket's blobs. If the {@link BlobListOption#currentDirectory()} option is provided,
   * results are returned in a directory-like mode.
   *
   * @throws StorageException upon failure
   */
  Page<Blob> list(String bucket, BlobListOption... options);

  /**
   * Updates bucket information.
   *
   * @return the updated bucket
   * @throws StorageException upon failure
   */
  Bucket update(BucketInfo bucketInfo, BucketTargetOption... options);

  /**
   * Updates blob information. Original metadata are merged with metadata in the provided
   * {@code blobInfo}. To replace metadata instead you first have to unset them. Unsetting metadata
   * can be done by setting the provided {@code blobInfo}'s metadata to {@code null}.
   *
   * <p>Example usage of replacing blob's metadata:
   * <pre> {@code
   * service.update(BlobInfo.builder("bucket", "name").metadata(null).build());
   * service.update(BlobInfo.builder("bucket", "name").metadata(newMetadata).build());
   * }
   * </pre>
   *
   * @return the updated blob
   * @throws StorageException upon failure
   */
  Blob update(BlobInfo blobInfo, BlobTargetOption... options);

  /**
   * Updates blob information. Original metadata are merged with metadata in the provided
   * {@code blobInfo}. To replace metadata instead you first have to unset them. Unsetting metadata
   * can be done by setting the provided {@code blobInfo}'s metadata to {@code null}.
   *
   * <p>Example usage of replacing blob's metadata:
   * <pre> {@code
   * service.update(BlobInfo.builder("bucket", "name").metadata(null).build());
   * service.update(BlobInfo.builder("bucket", "name").metadata(newMetadata).build());
   * }
   * </pre>
   *
   * @return the updated blob
   * @throws StorageException upon failure
   */
  Blob update(BlobInfo blobInfo);

  /**
   * Deletes the requested bucket.
   *
   * @return {@code true} if bucket was deleted, {@code false} if it was not found
   * @throws StorageException upon failure
   */
  boolean delete(String bucket, BucketSourceOption... options);

  /**
   * Deletes the requested blob.
   *
   * @return {@code true} if blob was deleted, {@code false} if it was not found
   * @throws StorageException upon failure
   */
  boolean delete(String bucket, String blob, BlobSourceOption... options);

  /**
   * Deletes the requested blob.
   *
   * @return {@code true} if blob was deleted, {@code false} if it was not found
   * @throws StorageException upon failure
   */
  boolean delete(BlobId blob, BlobSourceOption... options);

  /**
   * Deletes the requested blob.
   *
   * @return {@code true} if blob was deleted, {@code false} if it was not found
   * @throws StorageException upon failure
   */
  boolean delete(BlobId blob);

  /**
   * Sends a compose request.
   *
   * @return the composed blob
   * @throws StorageException upon failure
   */
  Blob compose(ComposeRequest composeRequest);

  /**
   * Sends a copy request. This method copies both blob's data and information. To override source
   * blob's information supply a {@code BlobInfo} to the
   * {@code CopyRequest} using either
   * {@link Storage.CopyRequest.Builder#target(BlobInfo, Storage.BlobTargetOption...)} or
   * {@link Storage.CopyRequest.Builder#target(BlobInfo, Iterable)}.
   *
   * <p>This method returns a {@link CopyWriter} object for the provided {@code CopyRequest}. If
   * source and destination objects share the same location and storage class the source blob is
   * copied with one request and {@link CopyWriter#result()} immediately returns, regardless of the
   * {@link CopyRequest#megabytesCopiedPerChunk} parameter. If source and destination have different
   * location or storage class {@link CopyWriter#result()} might issue multiple RPC calls depending
   * on blob's size.
   *
   * <p>Example usage of copy:
   * <pre> {@code BlobInfo blob = service.copy(copyRequest).result();}
   * </pre>
   * To explicitly issue chunk copy requests use {@link CopyWriter#copyChunk()} instead:
   * <pre> {@code
   * CopyWriter copyWriter = service.copy(copyRequest);
   * while (!copyWriter.isDone()) {
   *     copyWriter.copyChunk();
   * }
   * BlobInfo blob = copyWriter.result();
   * }
   * </pre>
   *
   * @return a {@link CopyWriter} object that can be used to get information on the newly created
   *     blob or to complete the copy if more than one RPC request is needed
   * @throws StorageException upon failure
   * @see <a href="https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite">Rewrite</a>
   */
  CopyWriter copy(CopyRequest copyRequest);

  /**
   * Reads all the bytes from a blob.
   *
   * @return the blob's content
   * @throws StorageException upon failure
   */
  byte[] readAllBytes(String bucket, String blob, BlobSourceOption... options);

  /**
   * Reads all the bytes from a blob.
   *
   * @return the blob's content
   * @throws StorageException upon failure
   */
  byte[] readAllBytes(BlobId blob, BlobSourceOption... options);

  /**
   * Sends a batch request.
   *
   * @return the batch response
   * @throws StorageException upon failure
   */
  BatchResponse submit(BatchRequest batchRequest);

  /**
   * Returns a channel for reading the blob's content. The blob's latest generation is read. If the
   * blob changes while reading (i.e. {@link BlobInfo#etag()} changes), subsequent calls to
   * {@code blobReadChannel.read(ByteBuffer)} may throw {@link StorageException}.
   *
   * <p>The {@link BlobSourceOption#generationMatch(long)} option can be provided to ensure that
   * {@code blobReadChannel.read(ByteBuffer)} calls will throw {@link StorageException} if blob`s
   * generation differs from the expected one.
   *
   * @throws StorageException upon failure
   */
  ReadChannel reader(String bucket, String blob, BlobSourceOption... options);

  /**
   * Returns a channel for reading the blob's content. If {@code blob.generation()} is set
   * data corresponding to that generation is read. If {@code blob.generation()} is {@code null}
   * the blob's latest generation is read. If the blob changes while reading (i.e.
   * {@link BlobInfo#etag()} changes), subsequent calls to {@code blobReadChannel.read(ByteBuffer)}
   * may throw {@link StorageException}.
   *
   * <p>The {@link BlobSourceOption#generationMatch()} and
   * {@link BlobSourceOption#generationMatch(long)} options can be used to ensure that
   * {@code blobReadChannel.read(ByteBuffer)} calls will throw {@link StorageException} if the
   * blob`s generation differs from the expected one.
   *
   * @throws StorageException upon failure
   */
  ReadChannel reader(BlobId blob, BlobSourceOption... options);

  /**
   * Creates a blob and return a channel for writing its content. By default any md5 and crc32c
   * values in the given {@code blobInfo} are ignored unless requested via the
   * {@code BlobWriteOption.md5Match} and {@code BlobWriteOption.crc32cMatch} options.
   *
   * @throws StorageException upon failure
   */
  WriteChannel writer(BlobInfo blobInfo, BlobWriteOption... options);

  /**
   * Generates a signed URL for a blob. If you have a blob that you want to allow access to for a
   * fixed amount of time, you can use this method to generate a URL that is only valid within a
   * certain time period. This is particularly useful if you don't want publicly accessible blobs,
   * but also don't want to require users to explicitly log in. Signing a URL requires
   * a service account signer. If a {@link ServiceAccountAuthCredentials} or an
   * {@link AppEngineAuthCredentials} was passed to
   * {@link StorageOptions.Builder#authCredentials(AuthCredentials)} or the default credentials are
   * being used and the environment variable {@code GOOGLE_APPLICATION_CREDENTIALS} is set, then
   * {@code signUrl} will use that credentials to sign the URL. If the credentials passed to
   * {@link StorageOptions} do not implement {@link ServiceAccountSigner} (this is the case for
   * Compute Engine credentials and Google Cloud SDK credentials) then {@code signUrl} will throw an
   * {@link IllegalStateException} unless an implementation of {@link ServiceAccountSigner} is
   * passed using the {@link SignUrlOption#signWith(ServiceAccountSigner)} option.
   *
   * <p>A service account signer is looked for in the following order:
   * <ol>
   *   <li>The signer passed with the option {@link SignUrlOption#signWith(ServiceAccountSigner)}
   *   <li>The credentials passed to {@link StorageOptions.Builder#authCredentials(AuthCredentials)}
   *   <li>The default credentials, if no credentials were passed to {@link StorageOptions}
   * </ol>
   *
   * <p>Example usage of creating a signed URL that is valid for 2 weeks, using the default
   * credentials for signing the URL:
   * <pre> {@code
   * service.signUrl(BlobInfo.builder("bucket", "name").build(), 14, TimeUnit.DAYS);
   * }</pre>
   *
   * <p>Example usage of creating a signed URL passing the
   * {@link SignUrlOption#signWith(ServiceAccountSigner)} option, that will be used for signing the
   * URL:
   * <pre> {@code
   * service.signUrl(BlobInfo.builder("bucket", "name").build(), 14, TimeUnit.DAYS,
   *     SignUrlOption.signWith(
   *         AuthCredentials.createForJson(new FileInputStream("/path/to/key.json"))));
   * }</pre>
   *
   * @param blobInfo the blob associated with the signed URL
   * @param duration time until the signed URL expires, expressed in {@code unit}. The finest
   *     granularity supported is 1 second, finer granularities will be truncated
   * @param unit time unit of the {@code duration} parameter
   * @param options optional URL signing options
   * @throws IllegalStateException if {@link SignUrlOption#signWith(ServiceAccountSigner)} was not
   *     used and no implementation of {@link ServiceAccountSigner} was provided to
   *     {@link StorageOptions}
   * @throws IllegalArgumentException if {@code SignUrlOption.withMd5()} option is used and
   *     {@code blobInfo.md5()} is {@code null}
   * @throws IllegalArgumentException if {@code SignUrlOption.withContentType()} option is used and
   *     {@code blobInfo.contentType()} is {@code null}
   * @throws SigningException if the attempt to sign the URL failed
   * @see <a href="https://cloud.google.com/storage/docs/access-control#Signed-URLs">Signed-URLs</a>
   */
  URL signUrl(BlobInfo blobInfo, long duration, TimeUnit unit, SignUrlOption... options);

  /**
   * Gets the requested blobs. A batch request is used to perform this call.
   *
   * @param blobIds blobs to get
   * @return an immutable list of {@code Blob} objects. If a blob does not exist or access to it
   *     has been denied the corresponding item in the list is {@code null}.
   * @throws StorageException upon failure
   */
  List<Blob> get(BlobId... blobIds);

  /**
   * Updates the requested blobs. A batch request is used to perform this call. Original metadata
   * are merged with metadata in the provided {@code BlobInfo} objects. To replace metadata instead
   * you first have to unset them. Unsetting metadata can be done by setting the provided
   * {@code BlobInfo} objects metadata to {@code null}. See
   * {@link #update(BlobInfo)} for a code example.
   *
   * @param blobInfos blobs to update
   * @return an immutable list of {@code Blob} objects. If a blob does not exist or access to it
   *     has been denied the corresponding item in the list is {@code null}.
   * @throws StorageException upon failure
   */
  List<Blob> update(BlobInfo... blobInfos);

  /**
   * Deletes the requested blobs. A batch request is used to perform this call.
   *
   * @param blobIds blobs to delete
   * @return an immutable list of booleans. If a blob has been deleted the corresponding item in the
   *     list is {@code true}. If a blob was not found, deletion failed or access to the resource
   *     was denied the corresponding item is {@code false}.
   * @throws StorageException upon failure
   */
  List<Boolean> delete(BlobId... blobIds);
}
