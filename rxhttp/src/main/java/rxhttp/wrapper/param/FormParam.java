package rxhttp.wrapper.param;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import okhttp3.MultipartBody.Part;
import okhttp3.RequestBody;
import rxhttp.wrapper.annotations.NonNull;
import rxhttp.wrapper.annotations.Nullable;
import rxhttp.wrapper.entity.KeyValuePair;
import rxhttp.wrapper.utils.BuildUtil;
import rxhttp.wrapper.utils.CacheUtil;

/**
 * post、put、patch、delete请求
 * 参数以{application/x-www-form-urlencoded}形式提交
 * 当带有文件时，自动以{multipart/form-data}形式提交
 * 当调用{@link #setMultiForm()}方法，强制以{multipart/form-data}形式提交
 * <p>
 * User: ljx
 * Date: 2019-09-09
 * Time: 21:08
 */
public class FormParam extends AbstractBodyParam<FormParam> implements IPart<FormParam> {

    private boolean isMultiForm;

    private List<Part> mPartList;  //Part List
    private List<KeyValuePair> bodyParam; //Param list

    /**
     * @param url    request url
     * @param method {@link Method#POST}、{@link Method#PUT}、{@link Method#DELETE}、{@link Method#PATCH}
     */
    public FormParam(String url, Method method) {
        super(url, method);
    }

    @Override
    public FormParam add(String key, @Nullable Object value) {
        if (value == null) value = "";
        return add(new KeyValuePair(key, value));
    }

    public FormParam addEncoded(String key, @Nullable Object value) {
        if (value == null) value = "";
        return add(new KeyValuePair(key, value, true));
    }

    public FormParam addAllEncoded(@NonNull Map<String, ?> map) {
        for (Entry<String, ?> entry : map.entrySet()) {
            addEncoded(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public FormParam removeAllBody(String key) {
        final List<KeyValuePair> bodyParam = this.bodyParam;
        if (bodyParam == null) return this;
        Iterator<KeyValuePair> iterator = bodyParam.iterator();
        while (iterator.hasNext()) {
            KeyValuePair next = iterator.next();
            if (next.equals(key))
                iterator.remove();
        }
        return this;
    }

    public FormParam removeAllBody() {
        final List<KeyValuePair> bodyParam = this.bodyParam;
        if (bodyParam != null)
            bodyParam.clear();
        return this;
    }

    public FormParam set(String key, Object value) {
        removeAllBody(key);
        return add(key, value);
    }

    public FormParam setEncoded(String key, Object value) {
        removeAllBody(key);
        return addEncoded(key, value);
    }

    private FormParam add(KeyValuePair keyValuePair) {
        List<KeyValuePair> bodyParam = this.bodyParam;
        if (bodyParam == null) {
            bodyParam = this.bodyParam = new ArrayList<>();
        }
        bodyParam.add(keyValuePair);
        return this;
    }

    @Override
    public FormParam addPart(Part part) {
        List<Part> partList = mPartList;
        if (partList == null) {
            isMultiForm = true;
            partList = mPartList = new ArrayList<>();
        }
        partList.add(part);
        return this;
    }

    //set content-type to multipart/form-data
    public FormParam setMultiForm() {
        isMultiForm = true;
        return this;
    }

    @Override
    public RequestBody getRequestBody() {
        return isMultiForm() ? BuildUtil.buildFormRequestBody(bodyParam, mPartList)
            : BuildUtil.buildFormRequestBody(bodyParam);
    }

    public List<Part> getPartList() {
        return mPartList;
    }

    /**
     * @deprecated please user {@link #getBodyParam()} instead
     */
    @Deprecated
    public List<KeyValuePair> getKeyValuePairs() {
        return getBodyParam();
    }

    public List<KeyValuePair> getBodyParam() {
        return bodyParam;
    }

    public boolean isMultiForm() {
        return isMultiForm;
    }

    @Override
    public String buildCacheKey() {
        List<KeyValuePair> cachePairs = new ArrayList<>();
        List<KeyValuePair> queryPairs = getQueryParam();
        List<KeyValuePair> bodyPairs = bodyParam;
        if (queryPairs != null)
            cachePairs.addAll(queryPairs);
        if (bodyPairs != null)
            cachePairs.addAll(bodyPairs);
        List<KeyValuePair> pairs = CacheUtil.excludeCacheKey(cachePairs);
        return BuildUtil.getHttpUrl(getSimpleUrl(), pairs).toString();
    }

    @Override
    public String toString() {
        return BuildUtil.getHttpUrl(getSimpleUrl(), bodyParam).toString();
    }
}
