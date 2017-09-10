package vorto.repo;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.eclipse.vorto.repository.api.IModelRepository;
import org.eclipse.vorto.repository.api.IModelResolver;
import org.eclipse.vorto.repository.api.ModelId;
import org.eclipse.vorto.repository.api.ModelInfo;
import org.eclipse.vorto.repository.api.content.BooleanAttributeProperty;
import org.eclipse.vorto.repository.api.content.BooleanAttributePropertyType;
import org.eclipse.vorto.repository.api.content.FunctionblockModel;
import org.eclipse.vorto.repository.api.content.IPropertyAttribute;
import org.eclipse.vorto.repository.api.content.Infomodel;
import org.eclipse.vorto.repository.api.content.ModelProperty;
import org.eclipse.vorto.repository.api.content.Stereotype;
import org.eclipse.vorto.repository.api.mapping.IMapping;
import org.eclipse.vorto.repository.api.resolver.ResolveQuery;
import org.eclipse.vorto.repository.client.RepositoryClientBuilder;
import org.eclipse.vorto.service.mapping.DataInput;
import org.eclipse.vorto.service.mapping.DataMapperBuilder;
import org.eclipse.vorto.service.mapping.IDataMapper;
import org.eclipse.vorto.service.mapping.IMappingSpecification;
import org.eclipse.vorto.service.mapping.ditto.DittoMapper;
import org.eclipse.vorto.service.mapping.ditto.DittoOutput;
import org.omi.gateway.bluetooth.Characteristic;
import org.omi.gateway.bluetooth.Device;
import org.omi.gateway.bluetooth.InitSequenceElement;
import org.omi.gateway.bluetooth.Service;

public class Test {
	public static void main(String[] args)
	{
		int chIdx = 0;
		IModelRepository repository;
	    RepositoryClientBuilder builder;
	    IMapping mapping;

		builder = RepositoryClientBuilder.newBuilder().setBaseUrl("http://vorto.eclipse.org");	
		repository = builder.buildModelRepositoryClient();
		mapping = builder.buildIMappingClient();
	    
		String modelNumber = "4343323635302053656e736f72546167";
	 
		IModelResolver resolver = builder.buildModelResolverClient();
		System.out.println("Looking for model with number " + modelNumber);
		ModelInfo model = null;
		
		try {
			model = resolver.resolve(new ResolveQuery("blegatt", "modelNumber", modelNumber, "DeviceInfoProfile")).get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		System.out.println("### Found info model: " + model.getId().getPrettyFormat());

		Device device = new Device();
		
		IMappingSpecification mappingSpec = IMappingSpecification.newBuilder().modelId(model.getId().getPrettyFormat()).key("blegatt").build();
		
		Infomodel infomodel = mappingSpec.getInfoModel();

		for (ModelProperty fbprop : infomodel.getFunctionblocks())
		{
			Service service = new Service();
			System.out.println("Found model property (fb): " + fbprop.getName());

			ModelId fbid = (ModelId) fbprop.getType();		
			System.out.println("Found Model ID: " + fbid.getPrettyFormat());
			
			FunctionblockModel fb = mappingSpec.getFunctionBlock(fbid);
			System.out.println("### Found function block: " + fb.getDisplayName() + " of stereotypes ");
			for (Stereotype stereotype : fb.getStereotypes())
			{
				System.out.println(stereotype.getName() + ", ");
				if (stereotype.getAttributes().containsKey("uuid"))
				{
					String uuid = stereotype.getAttributes().get("uuid");
					
					System.out.println(fbprop.getName() + ": found service uuid = " + uuid);
					
					service.setUuid(UUID.fromString(uuid));
				}
			}
			
			for (ModelProperty prop : fb.getConfigurationProperties())
			{
				System.out.println("###### Found function block config prop " + prop.getName() + ": ");
				
				Characteristic ch = new Characteristic();
				
				for (IPropertyAttribute attr : prop.getAttributes())
				{
					System.out.println(" --- Attr  " + attr.toString());
				}
				for (Stereotype stereotype : prop.getStereotypes())
				{
					if (stereotype.getAttributes().containsKey("uuid"))
					{
						ch.setUuid(UUID.fromString(stereotype.getAttributes().get("uuid").toString()));
						service.addCharacteristics(ch);
						Short[] data = {1, 2, 3, 4, 5, 6};
						ch.setData(data);
					}
					if (stereotype.getAttributes().containsKey("onConnect"))
					{
						InitSequenceElement elt = new InitSequenceElement(ch, stereotype.getAttributes().get("onConnect"));
						device.addInitSequenceElement(elt);
						System.out.println("ON CONNECT: " + elt.getCharacteristic().getUuid() + " = " + elt.getValue());
					}
				}
				ch.setHandle(String.valueOf(chIdx++));
				device.addCharacteristic(ch);
			}
			for (ModelProperty prop : fb.getStatusProperties())
			{
				System.out.println("###### Found function block status prop " + prop.getName() + ": ");
				
				Characteristic ch = new Characteristic();
				Short[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9};
				ch.setData(data);
				
				for (IPropertyAttribute attr : prop.getAttributes())
				{
					System.out.println(" --- Attr  " + attr.toString());
					if (attr instanceof BooleanAttributeProperty)
					{
						BooleanAttributeProperty attrprop = (BooleanAttributeProperty)attr;
						if ((attrprop.getType() == BooleanAttributePropertyType.EVENTABLE) && (attrprop.isValue()))
						{
							ch.setNotified(true);
							System.out.println("    -> notification");
						}
					}
				}
				for (Stereotype stereotype : prop.getStereotypes())
				{
					if (/* stereotype.getName().equals("Characteristic") && */stereotype.getAttributes().containsKey("uuid"))
					{
						ch.setUuid(UUID.fromString(stereotype.getAttributes().get("uuid").toString()));
						service.addCharacteristics(ch);
					}
					for (String key : stereotype.getAttributes().keySet())
					{
						System.out.println(" --- Map " + key + " to " + stereotype.getAttributes().get(key));
					}
				}
				ch.setHandle(String.valueOf(chIdx++));
				device.addCharacteristic(ch);
			}	
			
			device.addService(service);
		}
		
		System.out.println("Mapping BLE data to Vorto Model 1");
		DataMapperBuilder mapperBuilder = IDataMapper.newBuilder();
		
		System.out.println("Mapping BLE data to Vorto Model 2");
		mapperBuilder.withSpecification(mappingSpec);
		
		System.out.println("Mapping BLE data to Vorto Model 3");
		DittoMapper mapper = mapperBuilder.buildDittoMapper();
		
		System.out.println("Mapping BLE data to Vorto Model 4");
		DittoOutput mappedDittoOutput = mapper.map(DataInput.newInstance().fromObject(device));
	
		System.out.println(mappedDittoOutput.toJson());
	}
}
