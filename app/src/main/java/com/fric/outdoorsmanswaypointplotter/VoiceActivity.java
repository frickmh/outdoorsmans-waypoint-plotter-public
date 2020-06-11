package com.fric.outdoorsmanswaypointplotter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.List;

/**
 * A very simple application to handle Voice Recognition intents
 * and display the results
 */
public class VoiceActivity extends Activity
{

    private static final int REQUEST_CODE = 1234;
    private ListView wordsList;
    private EditText editTextLocationNotes;

    public static final String MyPREFERENCES = "MyPrefs" ;
    public static final String PlayChimePref = "playChimeKey";

    //private int systemVolume;
    private boolean systemVolumeChanged = false;
    private boolean recogRunning = false;

    ToggleButton toggleButtonPlayChime;

    /**
     * Called with the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.voice_recog);

        Button speakButton = (Button) findViewById(R.id.speakButton);
        Button buttonOK = (Button) findViewById(R.id.buttonOK);
        Button buttonCancel = (Button) findViewById(R.id.buttonCancel);
        Button buttonClearText = (Button) findViewById(R.id.buttonClearText);
        toggleButtonPlayChime = (ToggleButton) findViewById(R.id.toggleButtonPlayChime);

        final SharedPreferences sharedPreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);

        if (sharedPreferences.contains(PlayChimePref))
            toggleButtonPlayChime.setChecked(sharedPreferences.getBoolean(PlayChimePref, true));

        toggleButtonPlayChime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!toggleButtonPlayChime.isChecked()) {
                    Toast.makeText(getApplicationContext(), "Suppressing chime will mute the system volume until you close this dialog.  " +
                            "If volume is not reset, you can change it back in your device's Settings under Sound -> Volumes.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        buttonOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AudioManager amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);

                Log.i("buttonOk.onClick","Unmuting stream...");

                amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
                systemVolumeChanged = false;

                Intent intentResultData = new Intent();

                intentResultData.putExtra("VoiceString", editTextLocationNotes.getText().toString());

                if (getParent() == null) {
                    setResult(Activity.RESULT_OK, intentResultData);
                } else {
                    getParent().setResult(Activity.RESULT_OK, intentResultData);
                }

                SharedPreferences.Editor editor = sharedPreferences.edit();

                editor.putBoolean(PlayChimePref, toggleButtonPlayChime.isChecked());

                editor.commit();

                finish();
            }
        });

        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioManager amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);

                Log.i("buttonCancel.onClick","Unmuting stream...");

                amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
                systemVolumeChanged = false;

                Intent intentResultData = new Intent();

                if (getParent() == null) {
                    setResult(Activity.RESULT_CANCELED, intentResultData);
                } else {
                    getParent().setResult(Activity.RESULT_CANCELED, intentResultData);
                }
                finish();
            }
        });

        buttonClearText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editTextLocationNotes.setText("");
            }
        });

        wordsList = (ListView) findViewById(R.id.list);
        editTextLocationNotes = (EditText)findViewById(R.id.editTextLocationNotes);

        // Disable button if no recognition service is present
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (activities.size() == 0)
        {
            speakButton.setEnabled(false);
            speakButton.setText("Recognizer not present");
        }

    }


    @Override
    protected void onPause() {
        super.onPause();
        AudioManager amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);

        if (!recogRunning) {
            Log.i("onPause", "Unmuting stream...");
            amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            systemVolumeChanged = false;

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        AudioManager amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);

        Log.i("onDestroy","Unmuting stream...");

        amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        systemVolumeChanged = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Handle the action of the button being clicked
     */
    public void speakButtonClicked(View v)
    {
        startVoiceRecognitionActivity();
    }

    /**
     * Fire an intent to start the voice recognition activity.
     */
    private void startVoiceRecognitionActivity()
    {
        AudioManager amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);


        recogRunning = true;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Record Location Notes...");
        startActivityForResult(intent, REQUEST_CODE);

        if (!toggleButtonPlayChime.isChecked())
        {
            Log.i("startVoiceRecog","Muting stream...");

            amanager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            systemVolumeChanged = true;
        }
        else
        {
            Log.i("startVoiceRecog","Unmuting stream...");

            amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            systemVolumeChanged = false;
        }

    }

    /**
     * Handle the results from the voice recognition activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        recogRunning = false;

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK)
        {
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            wordsList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                    matches));

            wordsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    String s = (String)wordsList.getItemAtPosition(position);

                    editTextLocationNotes.append(s);

                }
            });
        }


        super.onActivityResult(requestCode, resultCode, data);
    }
}
