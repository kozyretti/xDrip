package com.eveningoutpost.dexdrip.cgm.nsfollow;

import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.BloodTest;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Sensor;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.Inevitable;
import com.eveningoutpost.dexdrip.utilitymodels.Unitized;
import com.eveningoutpost.dexdrip.cgm.nsfollow.messages.Entry;

import java.util.List;
import java.util.UUID;

import static com.eveningoutpost.dexdrip.models.BgReading.SPECIAL_FOLLOWER_PLACEHOLDER;

/**
 * jamorham
 *
 * Take a list of Nightscout entries and inject as BgReadings
 */

public class EntryProcessor {

    private static final String TAG = "NightscoutFollowEP";

    static synchronized void processEntries(final List<Entry> entries, final boolean live) {

        if (entries == null) return;

        final Sensor sensor = Sensor.createDefaultIfMissing();

        for (final Entry entry : entries) {
            if (entry != null) {
                UserError.Log.d(TAG, "ENTRY: " + entry.toS());
                UserError.Log.d(TAG, "Glucose value: " + Unitized.unitized_string_static(entry.sgv));

                final long recordTimestamp = entry.getTimeStamp();
                if (recordTimestamp > 0) {

                    if (entry.type.equals("mbg")) {
                        processMbg(entry, recordTimestamp);
                        continue;
                    }

                    final BgReading existing = BgReading.getForPreciseTimestamp(recordTimestamp, 10000);
                    if (existing == null) {
                        UserError.Log.d(TAG, "NEW NEW NEW New entry: " + entry.toS());

                        if (live) {
                            final BgReading bg = new BgReading();
                            bg.uuid = UUID.randomUUID().toString();
                            bg.timestamp = recordTimestamp;
                            bg.calculated_value = entry.sgv;
                            bg.raw_data = entry.unfiltered != 0 ? entry.unfiltered : SPECIAL_FOLLOWER_PLACEHOLDER;
                            bg.filtered_data = entry.filtered;
                            bg.noise = entry.noise + "";
                            // TODO need to handle slope??
                            bg.sensor = sensor;
                            bg.sensor_uuid = sensor.uuid;
                            bg.source_info = "Nightscout Follow";
                            bg.save();
                            Inevitable.task("entry-proc-post-pr",500, () -> bg.postProcess(false));
                        }
                    } else {
                       // break; // stop if we have this reading TODO are entries always in order?
                    }
                } else {
                    UserError.Log.e(TAG, "Could not parse a timestamp from: " + entry.toS());
                }

            } else {
                UserError.Log.d(TAG, "Entry is null");
            }
        }
    }

    private static void processMbg(final Entry entry, long timestamp_ms) {
        final BloodTest existing = BloodTest.byUUID(entry._id);
        if (existing == null) {
            final BloodTest bt = new BloodTest();
            bt.timestamp = timestamp_ms;
            bt.mgdl = entry.mbg;
            bt.uuid = entry._id;
            bt.created_timestamp = JoH.tsl();
            bt.state = BloodTest.STATE_VALID;
            bt.source = "Nightscout Follow";
            bt.saveit();
        }
        else {
            //if (d)
                UserError.Log.d(TAG, "Already a bloodtest with uuid: " + entry._id);
            if(existing.mgdl != entry.mbg || existing.timestamp / Constants.SECOND_IN_MS != timestamp_ms / Constants.SECOND_IN_MS) {
                UserError.Log.ueh(TAG, "Bloodtest changes from Nightscout: " + entry.mbg + " timestamp: " + JoH.dateTimeText(timestamp_ms) + " vs " + existing.mgdl + " " + JoH.dateTimeText(existing.timestamp));
                existing.timestamp = timestamp_ms;
                existing.mgdl = entry.mbg;
                existing.created_timestamp = JoH.tsl();
                existing.state = BloodTest.STATE_VALID;
                existing.source = "Nightscout Follow";
                existing.saveit();
            }
        }
    }

}
