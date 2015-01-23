/**
 * Odoo, Open Source Management Solution
 * Copyright (C) 2012-today Odoo SA (<http:www.odoo.com>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 *
 * Created on 13/1/15 3:40 PM
 */
package com.odoo.addons.crm;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.odoo.addons.crm.models.CRMCaseStage;
import com.odoo.addons.crm.models.CRMLead;
import com.odoo.base.addons.res.ResUsers;
import com.odoo.core.orm.ODataRow;
import com.odoo.core.orm.OValues;
import com.odoo.core.orm.fields.OColumn;
import com.odoo.core.utils.OActionBarUtils;
import com.odoo.core.utils.ODateUtils;
import com.odoo.crm.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import odoo.OArguments;
import odoo.controls.OForm;

public class CRMDetail extends ActionBarActivity {
    public static final String TAG = CRMDetail.class.getSimpleName();
    private Bundle extra;
    private OForm mForm;
    private ODataRow record;
    private CRMLead crmLead;
    private ActionBar actionBar;
    private Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.crm_detail);
        OActionBarUtils.setActionBar(this, true);
        actionBar = getSupportActionBar();
        crmLead = new CRMLead(this, null);
        extra = getIntent().getExtras();
        init();
    }

    private void init() {
        mForm = (OForm) findViewById(R.id.crmLeadForm);
        if (!extra.containsKey(OColumn.ROW_ID)) {
            if (extra.getString("type").equals(CRM.Type.Opportunities.toString())) {
                findViewById(R.id.opportunity_controls).setVisibility(View.VISIBLE);
            }
            mForm.initForm(null);
            actionBar.setTitle(R.string.label_new);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_action_navigation_close);
        } else {
            initFormValues();
        }
        mForm.setEditable(true);

    }

    private void initFormValues() {
        record = crmLead.browse(extra.getInt(OColumn.ROW_ID));
        if (record == null) {
            finish();
        }
        if (!record.getString("type").equals("lead")) {
            actionBar.setTitle(R.string.label_opportunity);
            findViewById(R.id.opportunity_controls).setVisibility(View.VISIBLE);
        } else {
            actionBar.setTitle(R.string.label_lead);
        }
        mForm.initForm(record);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_lead_detail, menu);
        this.menu = menu;
        toggleMenu();
        return true;
    }

    private void toggleMenu() {
        if (!extra.containsKey(OColumn.ROW_ID)) {
            menu.findItem(R.id.menu_lead_detail_more).setVisible(false);
        } else {
            initFormValues();
            menu.findItem(R.id.menu_lead_detail_more).setVisible(true);
            if (record.getString("type").equals(CRMLead.KEY_LEAD)) {
                menu.findItem(R.id.menu_lead_convert_to_quotation).setVisible(false);
                menu.findItem(R.id.menu_mark_won).setVisible(false);
            } else if (record.getString("type").equals(crmLead.KEY_OPPORTUNITY)) {
                menu.findItem(R.id.menu_lead_convert_to_opportunity).setVisible(false);
            }
        }
        menu.findItem(R.id.menu_lead_save).setVisible(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_action_navigation_close);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.menu_lead_save:
                OValues values = mForm.getValues();
                if (values != null) {
                    if (record != null) {
                        crmLead.update(record.getInt(OColumn.ROW_ID), values);
                    } else {
                        values.put("create_date", ODateUtils.getUTCDate());
                        values.put("user_id", ResUsers.myId(this));
                        CRMCaseStage stages = new CRMCaseStage(this, null);
                        ODataRow row = stages.browse(new String[]{"name"}, "name = ?", new String[]{"New"});
                        if (row != null) {
                            values.put("stage_id", row.getInt(OColumn.ROW_ID));
                            values.put("stage_name", row.getString("name"));
                        }
                        values.put("display_name", values.getString("partner_name"));
                        values.put("assignee_name", crmLead.getUser().getName());
                        crmLead.insert(values);
                    }
                    finish();

                }
                break;
            case R.id.menu_lead_convert_to_opportunity:
                try {
                    odoo.Odoo odoo = crmLead.getServerDataHelper().getOdoo();
                    OValues coValue = new OValues();
                    List<Integer> ids = new ArrayList<Integer>();
                    coValue.put("name", "convert");
                    coValue.put("action", (record.getInt("partner_id") != null) ? "exist"
                            : "create");
                    coValue.put(
                            "partner_id",
                            (record.getInt("partner_id") != null) ? record.getInt("partner_id") : false);
                    JSONObject context = new JSONObject();
                    context.put("stage_type", "lead");
                    context.put("active_id", record.getInt("id"));
                    context.put("active_ids",
                            new JSONArray().put(record.getInt("id")));
                    context.put("active_model", "crm.lead");
                    odoo.updateContext(context);
                    JSONObject args = new JSONObject();
                    for (String key : coValue.keys()) {
                        args.put(key, coValue.get(key));
                    }
                    JSONObject result = odoo.createNew(
                            "crm.lead2opportunity.partner", args);
                    int lead_to_opp_partner_id = result
                            .getInt("result");

                    OArguments arg = new OArguments();
                    arg.add(lead_to_opp_partner_id);
                    arg.add(context);
                    odoo.debug(true);
                    odoo.call_kw("crm.lead2opportunity.partner",
                            "action_apply", arg.get());
                    OValues vals = new OValues();
                    vals.put("type", "opportunity");
                    crmLead.update(record.getInt(OColumn.ROW_ID), vals);
                } catch (Exception e) {

                }
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
