package com.example.anroidaiassistant.contacts;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import com.example.anroidaiassistant.executor.CommandExecutionContext;
import com.example.anroidaiassistant.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContactRepository {
    private static final String TAG = "ContactRepository";
    private static final int MAX_CONTACT_CHOICES = 5;

    private final ContactMatcher contactMatcher;
    private final PhoneDialer phoneDialer;

    public ContactRepository(ContactMatcher contactMatcher, PhoneDialer phoneDialer) {
        this.contactMatcher = contactMatcher;
        this.phoneDialer = phoneDialer;
    }

    public List<ContactPhoneMatch> findContactPhoneMatches(
            String contactName,
            CommandExecutionContext executionContext
    ) {
        Context context = executionContext.getAndroidContext();
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            executionContext.showMessage("Contact permission is required to call contacts");
            return Collections.emptyList();
        }

        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        Map<String, ContactPhoneMatch> uniqueMatches = new LinkedHashMap<>();
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null) {
                return Collections.emptyList();
            }

            int displayNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            if (displayNameIndex < 0 || numberIndex < 0) {
                return Collections.emptyList();
            }

            while (cursor.moveToNext()) {
                String displayName = cursor.getString(displayNameIndex);
                String phoneNumber = cursor.getString(numberIndex);
                if (!hasText(displayName) || !hasText(phoneNumber)) {
                    continue;
                }

                ContactNameScore contactNameScore = contactMatcher.scoreContactName(contactName, displayName);
                if (contactNameScore.getScore() < ContactMatcher.MIN_CONTACT_FUZZY_SCORE) {
                    continue;
                }

                String normalizedPhoneNumber = phoneDialer.normalizePhoneNumber(phoneNumber);
                String key = normalizeAsciiText(displayName) + ":" + normalizedPhoneNumber;
                ContactPhoneMatch existing = uniqueMatches.get(key);
                if (existing == null || contactNameScore.getScore() > existing.getScore()) {
                    uniqueMatches.put(key, new ContactPhoneMatch(
                            displayName,
                            normalizedPhoneNumber,
                            contactNameScore.getScore(),
                            contactNameScore.isExact()
                    ));
                }
            }
        } catch (Exception exception) {
            Log.e(TAG, "Failed to query contacts", exception);
            return Collections.emptyList();
        }

        List<ContactPhoneMatch> matches = new ArrayList<>(uniqueMatches.values());
        matches.sort((left, right) -> {
            if (left.isExact() != right.isExact()) {
                return left.isExact() ? -1 : 1;
            }
            int scoreCompare = Integer.compare(right.getScore(), left.getScore());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
        });

        if (matches.size() > MAX_CONTACT_CHOICES) {
            return new ArrayList<>(matches.subList(0, MAX_CONTACT_CHOICES));
        }
        return matches;
    }

    private String normalizeAsciiText(String text) {
        return TextNormalizer.normalizeAsciiText(text);
    }

    private boolean hasText(String value) {
        return TextNormalizer.hasText(value);
    }
}