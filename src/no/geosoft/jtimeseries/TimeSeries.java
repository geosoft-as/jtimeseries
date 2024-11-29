package no.geosoft.jtimeseries;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import no.geosoft.jtimeseries.util.Formatter;
import no.geosoft.jtimeseries.util.JsonUtil;
import no.geosoft.jtimeseries.util.Util;

/**
 * Class representing the content of one TimeSeries.JSON entry.
 * A TimeSeries consists of a header, signal definitions, and signal data.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class TimeSeries
{
  /**
   * The time series header data as a single JSON object.
   * <p>
   * We keep all the metadata in JSON form as the TimeSeries class as such
   * does not take control of its entire content. Metadata may contain
   * anything the client like as long as it is valid JSON.
   */
  private JsonObject header_;

  /** The signals of this time series instance. */
  private final List<Signal> signals_ = new CopyOnWriteArrayList<>();

  /** Indicate if this instance includes signal data or not. */
  private boolean hasSignalData_;

  /**
   * Create a new time series instance.
   *
   * @param hasSignalData  Indicate if the time series includes signal data.
   */
  TimeSeries(boolean hasSignalData)
  {
    hasSignalData_ = hasSignalData;
  }

  /**
   * Create an empty time series instance.
   */
  public TimeSeries()
  {
    this(true); // It has all the signal data that exists (none)

    // Default empty header
    header_ = Json.createObjectBuilder().build();
  }

  /**
   * Create a time series instance as a copy of the specified one.
   *
   * @param timeSeries           Instance to copy. Non-null.
   * @param includeSignalValues  True to include signal values in the copy, false if not.
   * @throws IllegalArgumentException  If timeSeries is null.
   */
  public TimeSeries(TimeSeries timeSeries, boolean includeSignalValues)
  {
    if (timeSeries == null)
      throw new IllegalArgumentException("timeSeries cannot be null");

    // Create empty header
    header_ = Json.createObjectBuilder().build();

    // Populate with values from time series
    for (String key : timeSeries.getProperties())
      setProperty(key, timeSeries.getProperty(key));

    // Add a copy of the curves with or without curve values
    for (Signal signal : timeSeries.signals_)
      addSignal(new Signal(signal, includeSignalValues));
  }

  /**
   * Return whether this time series includes curve data
   * or not, i.e.&nbsp;if only header data was read or created.
   *
   * @return  True if bulk (signal) data is present, false otherwise.
   */
  public boolean hasSignalData()
  {
    return hasSignalData_;
  }

  /**
   * Set the header of this instance.
   *
   * @param header  JSON header object. Non-null.
   * @throws IllegalArgumentException  If header is null.
   */
  public void setHeader(JsonObject header)
  {
    if (header == null)
      throw new IllegalArgumentException("header cannot be null");

    synchronized (this) {
      // This is safe as JsonObject is immutable
      header_ = header;
    }
  }

  /**
   * Return the header of this time series as a single JSON object.
   *
   * @return  Header of this time series. Never null.
   */
  public JsonObject getHeader()
  {
    synchronized (this) {
      // This is safe as JsonObject is immutable
      return header_;
    }
  }

  /**
   * Set a string header property of this time series.
   *
   * @param key    Key of property to set. Non-null.
   * @param value  Associated value. Null to unset. Must be of type
   *               BigDecimal, BigInteger, Boolean, Double, Integer,
   *               Long, String, Date or JsonValue.
   * @throws IllegalArgumentException  If key is null or value is not of a
   *               legal primitive type.
   */
  public void setProperty(String key, Object value)
  {
    if (key == null)
      throw new IllegalArgumentException("key cannot be null");

    if (value != null &&
        !(value instanceof BigDecimal) &&
        !(value instanceof BigInteger) &&
        !(value instanceof Boolean) &&
        !(value instanceof Double) &&
        !(value instanceof Integer) &&
        !(value instanceof Long) &&
        !(value instanceof String) &&
        !(value instanceof Date) &&
        !(value instanceof JsonValue))
      throw new IllegalArgumentException("Invalid property type: " + value.getClass());

    JsonObjectBuilder objectBuilder = Json.createObjectBuilder();

    synchronized (this) {
      header_.forEach(objectBuilder::add); // Capture the existing content
      JsonUtil.add(objectBuilder, key, value); // Add the new value
      setHeader(objectBuilder.build());
    }
  }

  /**
   * Set a double array header property of this time series.
   *
   * @param key     Key of property to set. Non-null.
   * @param values  Associated double array. Null to unset.
   */
  public void setProperty(String key, double[] values)
  {
    if (values == null) {
      setProperty(key, null);
      return;
    }

    JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
    for (double value : values)
      arrayBuilder.add(value);

    JsonObjectBuilder objectBuilder = Json.createObjectBuilder();

    synchronized (this) {
      header_.forEach(objectBuilder::add); // Capture the existing content
      objectBuilder.add(key, arrayBuilder); // Add the new array
      setHeader(objectBuilder.build());
    }
  }

  /**
   * Return all the header property keys of this time series.
   *
   * @return  All property keys of this time series. Never null.
   */
  public Set<String> getProperties()
  {
    return getHeader().keySet();
  }

  /**
   * Return header property for the specified key.
   * <p>
   * This is a generic method for clients that knows custom content
   * of the time series. It is up to the client program to parse the returned
   * content into the appropriate type.
   *
   * @param key  Key of property to get. Non-null.
   * @return     The associated value, or null if not found.
   * @throws IllegalArgumentException  If key is null.
   */
  public Object getProperty(String key)
  {
    if (key == null)
      throw new IllegalArgumentException("key cannot be null");

    JsonValue value = getHeader().get(key);
    return value != null ? JsonUtil.getValue(value) : null;
  }

  /**
   * Return header property for the specified key as a string.
   *
   * @param key  Key of property to get. Non-null.
   * @return     The associated value as a string. Null if not found, or
   *             not compatible with the string type.
   * @throws IllegalArgumentException  If key is null.
   */
  public String getPropertyAsString(String key)
  {
    if (key == null)
      throw new IllegalArgumentException("key cannot be null");

    Object object = getProperty(key);

    // Since Util.getAsType() return null for empty string
    if (object instanceof String && object.toString().isEmpty())
      return "";

    return (String) Util.getAsType(object, String.class);
  }

  /**
   * Return header property for the specified key as a double.
   *
   * @param key  Key of property to get. Non-null.
   * @return     The associated value as a double. Null if not found, or
   *             not compatible with the double type.
   * @throws IllegalArgumentException  If key is null.
   */
  public Double getPropertyAsDouble(String key)
  {
    if (key == null)
      throw new IllegalArgumentException("key cannot be null");

    return (Double) Util.getAsType(getProperty(key), Double.class);
  }

  /**
   * Return header property for the specified key as an integer.
   *
   * @param key  Key of property to get. Non-null.
   * @return     The associated value as an integer. Null if not found, or
   *             not compatible with the integer type.
   * @throws IllegalArgumentException  If key is null.
   */
  public Integer getPropertyAsInteger(String key)
  {
    if (key == null)
      throw new IllegalArgumentException("key cannot be null");

    return (Integer) Util.getAsType(getProperty(key), Integer.class);
  }

  /**
   * Return header property for the specified key as a boolean.
   *
   * @param key  Key of property to get. Non-null.
   * @return     The associated value as a boolean. Null if not found, or
   *             not compatible with the boolean type.
   * @throws IllegalArgumentException  If key is null.
   */
  public Boolean getPropertyAsBoolean(String key)
  {
    if (key == null)
      throw new IllegalArgumentException("key cannot be null");

    return (Boolean) Util.getAsType(getProperty(key), Boolean.class);
  }

  /**
   * Return header property for the specified key as date.
   *
   * @param key  Key of property to get. Non-null.
   * @return     The associated value as a date. Null if not found, or
   *             not compatible with the date type.
   * @throws IllegalArgumentException  If key is null.
   */
  public Date getPropertyAsDate(String key)
  {
    if (key == null)
      throw new IllegalArgumentException("key cannot be null");

    return (Date) Util.getAsType(getProperty(key), Date.class);
  }

  public double[] getPropertyAsDoubleArray(String key)
  {
    if (key == null)
      throw new IllegalArgumentException("key cannot be null");

    JsonValue value = getHeader().get(key);

    if (value == null || value.getValueType() != JsonValue.ValueType.ARRAY)
      return null;

    JsonArray jsonArray = value.asJsonArray();
    return JsonUtil.getDoubleArray(jsonArray);
  }

  /**
   * Return name of this timer series.
   *
   * @return  Name of this tim series. Null if none provided.
   */
  public String getName()
  {
    return getPropertyAsString(WellKnownProperty.NAME.getKey());
  }

  /**
   * Set name of this time series.
   *
   * @param name  Name to set. Null to unset.
   */
  public void setName(String name)
  {
    setProperty(WellKnownProperty.NAME.getKey(), name);
  }

  /**
   * Get description of this log.
   *
   * @return  Description of this log. Null if none provided.
   */
  public String getDescription()
  {
    return getPropertyAsString(WellKnownProperty.DESCRIPTION.getKey());
  }

  /**
   * Set description of this log.
   *
   * @param description  Description to set. Null to unset.
   */
  public void setDescription(String description)
  {
    setProperty(WellKnownProperty.DESCRIPTION.getKey(), description);
  }

  /**
   * Return the source (system or process) of this log.
   *
   * @return  Source of this log. Null if none provided.
   */
  public String getSource()
  {
    return getPropertyAsString(WellKnownProperty.SOURCE.getKey());
  }

  /**
   * Set source (system or process) of this log.
   *
   * @param source  Source of this log. Null to unset.
   */
  public void setSource(String source)
  {
    setProperty(WellKnownProperty.SOURCE.getKey(), source);
  }

  /**
   * Return the organization behind this time series.
   *
   * @return  Organization behind this time series. Null if none provided.
   */
  public String getOrganization()
  {
    return getPropertyAsString(WellKnownProperty.ORGANIZATION.getKey());
  }

  /**
   * Set organization behind this time series.
   *
   * @param organization  Organization behind this log. Null to unset.
   */
  public void setOrganization(String organization)
  {
    setProperty(WellKnownProperty.ORGANIZATION.getKey(), organization);
  }

  /**
   * Return license information for the data of this time series.
   *
   * @return  License information for the data of this time series. Null if none provided.
   */
  public String getLicense()
  {
    return getPropertyAsString(WellKnownProperty.LICENSE.getKey());
  }

  /**
   * Set license information for the data of this time series.
   *
   * @param license  License information for the data of this time series. Null to unset.
   */
  public void setLicense(String license)
  {
    setProperty(WellKnownProperty.LICENSE.getKey(), license);
  }

  public double[] getLocation()
  {
    return getPropertyAsDoubleArray(WellKnownProperty.LOCATION.getKey());
  }

  public void setLocation(double latitude, double longitude)
  {
    setProperty(WellKnownProperty.LOCATION.getKey(), new double[] {latitude, longitude});
  }

  /**
   * Return URI location of the data object in case this is kept separate.
   *
   * @return  URI location of the data object. Null if data is kept locale.
   */
  public String getDataUri()
  {
    return getPropertyAsString(WellKnownProperty.DATA_URI.getKey());
  }

  /**
   * Set URI for the data object in case this is kept separate.
   *
   * @param dataUri  URI to the data object. Null if data is kept local.
   */
  public void setDataUri(String dataUri)
  {
    setProperty(WellKnownProperty.DATA_URI.getKey(), dataUri);
  }

  /**
   * Return value type of the index of this log, typically Double.class
   * or Date.class.
   *
   * @return Value type of the index of this log. Never null.
   *         If the log has no curves, Double.class is returned.
   */
  public Class<?> getIndexValueType()
  {
    return signals_.isEmpty() ? Date.class : signals_.get(0).getValueType();
  }

  /**
   * Return start index of this log.
   * <p>
   * <b>NOTE: </b> This property is taken from the header, and may not
   * necessarily be in accordance with the <em>actual</em> data of the log.
   *
   * @return Start index of this log. The type will be according to
   *         the type of the index curve, @see #getIndexValueType.
   */
  public Object getStartIndex()
  {
    return getIndexValueType() == Date.class ?
      getPropertyAsDate(WellKnownProperty.TIME_START.getKey()) :
      getPropertyAsDouble(WellKnownProperty.TIME_START.getKey());
  }

  /**
   * Return the <em>actual</em> start index of this log.
   *
   * @return  The actual start index of this log. Null if the log has no values.
   */
  public Object getActualStartIndex()
  {
    Signal timeSignal = !signals_.isEmpty() ? signals_.get(0) : null;
    int nValues = timeSignal != null ? timeSignal.getNValues() : 0;
    return nValues > 0 ? timeSignal.getValue(0, 0) : null;
  }

  public Date getStartTime()
  {
    Object startIndex = getStartIndex();
    return startIndex instanceof Date ? (Date) startIndex : null;
  }

  public Date getActualStartTime()
  {
    Object actualStartIndex = getStartIndex();
    return actualStartIndex instanceof Date ? (Date) actualStartIndex : null;
  }

  /**
   * Set start index of this log in header.
   *
   * @param startIndex  Start index to set. Null to unset. The type should
   *                    be in accordance with the actual type of the index curve
   *                    of the log.
   */
  public void setStartIndex(Object startIndex)
  {
    if (startIndex instanceof Date)
      setProperty(WellKnownProperty.TIME_START.getKey(), startIndex);
    else
      setProperty(WellKnownProperty.TIME_START.getKey(), Util.getAsDouble(startIndex));
  }

  /**
   * Return end index of this log.
   * <p>
   * <b>NOTE: </b> This property is taken from header, and may not
   * necessarily be in accordance with the <em>actual</em> data of the log.
   *
   * @return End index of this log. The type will be according to
   *         the type of the index curve, @see #getIndexValueType.
   */
  public Object getEndIndex()
  {
    return getIndexValueType() == Date.class ?
      getPropertyAsDate(WellKnownProperty.TIME_END.getKey()) :
      getPropertyAsDouble(WellKnownProperty.TIME_END.getKey());
  }

  public Date getEndTime()
  {
    Object endIndex = getEndIndex();
    return endIndex instanceof Date ? (Date) endIndex : null;
  }

  /**
   * Return the <em>actual</em> end index of this log.
   *
   * @return  The actual end index of this log. Null if the log has no values.
   */
  public Object getActualEndIndex()
  {
    Signal timeSignal = !signals_.isEmpty() ? signals_.get(0) : null;
    int nValues = timeSignal != null ? timeSignal.getNValues() : 0;
    return nValues > 0 ? timeSignal.getValue(nValues - 1, 0) : null;
  }

  public Date getActualEndTime()
  {
    Object actualEndIndex = getEndIndex();
    return actualEndIndex instanceof Date ? (Date) actualEndIndex : null;
  }

  /**
   * Set end index of this log in the header.
   *
   * @param endIndex  End index to set. Null to unset. The type should
   *                  be in accordance with the actual type of the index curve
   *                  of the log.
   */
  public void setEndIndex(Object endIndex)
  {
    if (endIndex instanceof Date)
      setProperty(WellKnownProperty.TIME_END.getKey(), endIndex);
    else
      setProperty(WellKnownProperty.TIME_END.getKey(), Util.getAsDouble(endIndex));
  }

  /**
   * Return the regular step of this log.
   * <p>
   * <b>NOTE: </b> This property is taken from header, and may not
   * necessarily be in accordance with the <em>actual</em> data on the file.
   *
   * @return The step of the index curve of this log.
   *         Null should indicate that the log in irregular or the step is unknown.
   */
  public Double getStep()
  {
    return getPropertyAsDouble(WellKnownProperty.TIME_STEP.getKey());
  }

  /**
   * Return the <em>actual</em> step of the index curve of this log.
   *
   * @return  The actual step of the index curve.
   *          Null if the log has no data or the log set is irregular.
   */
  public Double getActualStep()
  {
    return computeStep();
  }

  /**
   * Set the regular step of the index curve of this log.
   *
   * @param step  Step to set. Null to indicate unknown or that the index is irregular.
   *              If the log set is time based, the step should be the number
   *              of <em>milliseconds</em> between samples.
   */
  public void setStep(Double step)
  {
    setProperty(WellKnownProperty.TIME_STEP.getKey(), step);
  }

  /**
   * Add the specified curve to this log. The first curve added to a log
   * is by convention the index curve.
   *
   * @param curve  Curve to add. Non-null.
   * @throws IllegalArgumentException  If curve is null.
   */
  void addSignal(Signal signal)
  {
    if (signal == null)
      throw new IllegalArgumentException("signal cannot be null");

    signals_.add(signal);
  }

  Signal getSignal(int signalNo)
  {
    return signals_.get(signalNo);
  }

  public String getSignalName(int signalNo)
  {
    return signals_.get(signalNo).getName();
  }

  public String getDescription(int signalNo)
  {
    return signals_.get(signalNo).getDescription();
  }

  public String getQuantity(int signalNo)
  {
    return signals_.get(signalNo).getQuantity();
  }

  public String getUnit(int signalNo)
  {
    return signals_.get(signalNo).getUnit();
  }

  public Class<?> getValueType(int signalNo)
  {
    return signals_.get(signalNo).getValueType();
  }

  public int getNDimensions(int signalNo)
  {
    return signals_.get(signalNo).getNDimensions();
  }

  public int getSize(int signalNo)
  {
    return signals_.get(signalNo).getSize();
  }

  public Object getValue(int signalNo, int index, int dimension)
  {
    return signals_.get(signalNo).getValue(index, dimension);
  }

  public Object getValue(int signalNo, int index)
  {
    return signals_.get(signalNo).getValue(index, 0);
  }

  public Object[] getRange(int signalNo)
  {
    return signals_.get(signalNo).getRange();
  }


  public int findSignal(String signalName)
  {
    if (signalName == null)
      throw new IllegalArgumentException("signalName cannot be null");

    for (int signalNo = 0; signalNo < getNSignals(); signalNo++) {
      if (getSignalName(signalNo).equals(signalName))
        return signalNo;
    }

    // Not found
    return -1;
  }

  /**
   * Return the signals of this log. The first signal
   * is by convention always the index, typically the times.
   *
   * @return  The signals of this time series. Never null.
   */
  List<Signal> getSignals()
  {
    return Collections.unmodifiableList(signals_);
  }

  /**
   * Replace the present set of signals.
   * <p>
   * This method is called by the reader to populate a TimeSeries instance
   * that initially was read without bulk data.
   *
   * @param signals  Signals to set. Non-null.
   */
  void setSignals(List<Signal> signals)
  {
    assert signals != null : "signals cannot be null";

    synchronized (signals_) {
      signals_.clear();
      signals_.addAll(signals);
    }

    hasSignalData_ = true;
  }

  /**
   * Return the number of curves in this log.
   *
   * @return  Number of curves in this log. [0,&gt;.
   */
  public int getNSignals()
  {
    return signals_.size();
  }

  /**
   * Return the number of values (per curve) in this log.
   *
   * @return  Number of values in this log. [0,&gt;.
   */
  public int getNValues()
  {
    return signals_.isEmpty() ? 0 : signals_.get(0).getNValues();
  }

  /**
   * Clear curve data from all curves of this log.
   */
  public void clearSignals()
  {
    for (Signal signal : signals_)
      signal.clear();
  }

  /**
   * Set curve capacity to actual size to save memory.
   * The assumption is that the curves will not grow any further.
   */
  void trimSignals()
  {
    for (Signal signal : signals_)
      signal.trim();
  }

  /**
   * Return number of significant digits to use to properly represent
   * the values of the specified signal.
   *
   * @param curve    Curve to consider. Non-null.
   * @param isIndex  True if curve is an index curve, false otherwise.
   * @return         The number of significant digits to use for the
   *                 specified curve. [0,&gt;.
   */
  private int getNSignificantDigits(Signal signal, boolean isIndex)
  {
    assert signal != null : "curve cannot be null";

    Class<?> valueType = signal.getValueType();

    // Limit to platform capabilities (see Util.getNSignificantDigits)
    int maxSignificantDigits = 10;

    if (valueType != Double.class && valueType != Float.class)
      return 0;

    if (signal.getNValues() == 0)
      return 0;

    if (!isIndex)
      return maxSignificantDigits;

    //
    // TODO!
    // Special treatment for the time signal so we don't accidently
    // lose accuracy; making a regular time series irregular.
    //

    Object[] range = signal.getRange();
    if (range[0] == null || range[1] == null)
      return maxSignificantDigits;

    Double step = computeStep();
    if (step == null || step == 0.0)
      return maxSignificantDigits;

    double minValue = Util.getAsDouble(range[0]);
    double maxValue = Util.getAsDouble(range[1]);

    double max = Math.max(Math.abs(minValue), Math.abs(maxValue));

    return Util.getNSignificantDigits(max, step);
  }

  /**
   * Create a formatter for the data of the specified signal.
   *
   * @param curve         Curve to create formatter for. Non-null.
   * @param isIndexCurve  True if curve is the index curve, false otherwise.
   * @return  A formatter that can be used to write the curve data.
   *                      Null if the log data is not of numeric type.
   */
  Formatter createFormatter(int signalNo)
  {
    assert signalNo >= 0 && signalNo < getNSignals() : "Invalid signalNo: " + signalNo;

    Signal signal = signals_.get(signalNo);
    boolean isIndex = signalNo == 0;

    Class<?> valueType = signal.getValueType();
    if (valueType != Double.class && valueType != Float.class)
      return null;

    int nDimensions = signal.getNDimensions();
    int nValues = signal.getNValues();

    double[] values = new double[nValues * nDimensions];

    for (int index = 0; index < nValues; index++)
      for (int dimension = 0; dimension < nDimensions; dimension++)
        values[dimension * nValues + index] = Util.getAsDouble(signal.getValue(index, dimension));

    int nSignificantDigits = getNSignificantDigits(signal, isIndex);

    return new Formatter(values, nSignificantDigits, null, null);
  }

  /**
   * A simple way to keep track of latency within a system or a pipeline
   * is to add time stamp or latency signal to the time series. This method will
   * add the specified latency signal to the given time series and compute latency
   * from similar signals added earlier.
   * <p>
   * The signal should have a numeric suffix, like TIME_T8 etc. or
   * such a suffix will be added.
   * <p>
   * The first signal of this pattern added will contain a timestamp (long
   * values of milliseconds since Epoch) while later signals added will contain
   * the latency (in milliseconds) since the <em>previous</em> signal was added.
   * <p>
   * Signals may not be a consecutive sequence. TIME_T0 can be followed
   * by TIME_T4 and so on.
   *
   * @param signalName         Name of signal to add. A numeric suffix is added if
   *                           the name doesn't contain one already.
   * @param signalDescription  Signal description, typically describing the
   *                           performed task responsible for the latency. May be null.
   * @param isTotalLatency     True to make a grand total of latency signals added
   *                           earlier, false to make it a regular latency signal.
   * @return                   Actual name of the curve added. Never null.
   * @throws IllegalArgumentException  If timeSeries or curveName is null.
   */
  public String addLatencySignal(String signalName,
                                 String signalDescription,
                                 boolean isTotalLatency)
  {
    if (signalName == null)
      throw new IllegalArgumentException("signalName cannot be null");

    //
    // Split signalName into base name and numeric suffix.
    // If it doesn't end in a number, suffix will be null.
    //
    String baseName = signalName;
    String suffixString = null;

    Pattern pattern = Pattern.compile("([a-zA-Z_\\s]*)(.*)$");
    Matcher matcher = pattern.matcher(signalName);
    if (matcher.find()) {
      baseName = matcher.group(1);
      suffixString = matcher.group(2);
    }

    //
    // Determine suffix. Start with the one provided (or 0), but check
    // if this exists and pick the next available one.
    //
    int suffix = 0;
    try {
      suffix = Integer.parseInt(suffixString);
    }
    catch (NumberFormatException exception) {
      suffix = 0;
    }
    while (true) {
      String name = baseName + suffix;
      if (findSignal(name) == -1)
        break;
      suffix++;
    }

    //
    // Create the new signal
    //
    String newSignalName = isTotalLatency ? baseName : baseName + suffix;
    Signal newLatencySignal = new Signal(newSignalName, signalDescription, "Time", "ms", Long.class, 1);

    //
    // Find all existing latency signals. Since latency signals
    // may not be consecutive we search a wide range.
    //
    List<Integer> latencySignals = new ArrayList<>();
    suffix = 0;
    while (suffix < 9999) {
      String name = baseName + suffix;
      int signalNo = findSignal(name);
      if (signalNo != -1)
        latencySignals.add(signalNo);
      suffix++;
    }

    //
    // Time right now.
    //
    long now = System.currentTimeMillis();

    //
    // If this is the first latency signal, we populate with this number,
    // otherwise we subtract all numbers proir to this one.
    //
    for (int i = 0; i < getNValues(); i++) {

      // Pick the time now and subtract value from the other latency curves
      Long totalLatency = now;

      for (int signalNo : latencySignals) {
        Object value = getValue(signalNo, i);
        Long latency = (Long) Util.getAsType(value, Long.class);

        // In the total latency case we only want to subtract the
        // initial time stamp.
        if (isTotalLatency && latency != null && latency < 10000000L)
          latency = 0L;

        totalLatency = latency != null && totalLatency != null ? totalLatency - latency : null;
      }

      newLatencySignal.addValue(totalLatency);
    }

    addSignal(newLatencySignal);
    return newSignalName;
  }

  /**
   * Find actual step value of this time series, being the distance between
   * values in the index curve. Three values are returned: the <em>minimum step</em>,
   * the <em>maximum step</em> and the <em>average step</em>. It is left to the caller
   * to decide if these numbers represents a <em>regular</em> or an <em>irregular</em>
   * log set.
   *
   * @param signal  Signal to get step from. Non-null.
   * @return        The (minimum, maximum and average) step value of the log.
   */
  private double[] findStep()
  {
    int nValues = getNValues();

    if (nValues < 2)
      return new double[] {0.0, 0.0, 0.0};

    double minStep = +Double.MAX_VALUE;
    double maxStep = -Double.MAX_VALUE;
    double averageStep = 0.0;

    int nSteps = 0;
    double indexValue0 = Util.getAsDouble(getValue(0, 0));
    for (int index = 1; index < nValues; index++) {
      double indexValue1 = Util.getAsDouble(getValue(0, index));
      double step = indexValue1 - indexValue0;

      nSteps++;

      if (step < minStep)
        minStep = step;

      if (step > maxStep)
        maxStep = step;

      averageStep += (step - averageStep) / nSteps;

      indexValue0 = indexValue1;
    }

    return new double[] {minStep, maxStep, averageStep};
  }

  /**
   * Based on the index curve, compute the step value of the specified log
   * as it will be reported in the <em>step</em> metadata.
   * <p>
   * The method uses the {@link JsonUtil#findStep} method to compute min, max and
   * average step, and then compare the largest deviation from the average
   * (min or max) to the average itself.
   * If this is within some limit (0.5% currently) the step is considered
   * regular.
   *
   * @param log  Log to compute step of. Non-null.
   * @return     The log step value. null if irregular.
   */
  private Double computeStep()
  {
    double[] step = findStep();

    double minStep = step[0];
    double maxStep = step[1];
    double averageStep = step[2];

    // Find largest deviation from average of the two
    double d = Math.max(Math.abs(minStep - averageStep), Math.abs(maxStep - averageStep));

    // Figure out if this is close enough to regard as equal
    // NOTE: If this number causes apparently regular log sets to appear irregular
    // we might consider adjusting it further, probably as high as 0.01 would be OK.
    boolean isEqual = d <= Math.abs(averageStep) * 0.005;

    return isEqual ? averageStep : null;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    StringBuilder s = new StringBuilder();
    s.append("-- Signal\n");

    s.append("Header:\n");
    for (String property : getProperties())
      s.append(property + ": " + getProperty(property));

    for (Signal signal : signals_)
      s.append(signal + "\n");

    return s.toString();
  }
}